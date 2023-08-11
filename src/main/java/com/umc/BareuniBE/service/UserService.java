package com.umc.BareuniBE.service;

import com.umc.BareuniBE.config.security.JwtTokenProvider;
import com.umc.BareuniBE.dto.*;
import com.umc.BareuniBE.entities.User;
import com.umc.BareuniBE.global.BaseException;
import com.umc.BareuniBE.global.enums.RoleType;
import com.umc.BareuniBE.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.umc.BareuniBE.global.BaseResponseStatus.*;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final LikeRepository likeRepository;

    private final CommentRepository commentRepository;

    private final BookingRepository bookingRepository;

    private final ScrapRepository scrapRepository;

    private final ReviewRepository reviewRepository;

    private final JwtTokenProvider jwtTokenProvider;

    private final RedisTemplate redisTemplate;

    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserRes.UserJoinRes join(UserReq.UserJoinReq request) throws BaseException {

        if(!request.getEmail().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+.[A-Za-z]{2,6}$"))
            throw new BaseException(POST_USERS_INVALID_EMAIL);

        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());

        if(userOptional.isEmpty()){

            String encryptedPw = encoder.encode(request.getPassword());

            User newUser = User.builder()
                    .email(request.getEmail())
                    .password(encryptedPw)
                    .nickname(request.getNickname())
                    .gender(request.getGender())
                    .age(request.getAge())
                    .ortho(request.isOrtho())
                    .role(RoleType.USER)
                    .provider(request.getProvider())
                    .build();
            //System.out.println("새로 가입하는 유저: "+newUser);
            User user = userRepository.saveAndFlush(newUser);
            return new UserRes.UserJoinRes(user);
        }else{
            throw new BaseException(POST_USERS_EXISTS_EMAIL);
        }
    }

    // 로그인
    public List<TokenDTO> login(UserReq.UserLoginReq request) throws BaseException {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BaseException(FAILED_TO_LOGIN)); // 가입안된 이메일
        if(!encoder.matches(request.getPassword(), user.getPassword())) {
            throw new BaseException(FAILED_TO_LOGIN); // 비밀번호 일치 X
        }

        // 토큰 발급해서
        TokenDTO refreshToken = jwtTokenProvider.createRefreshToken(user.getEmail());
        TokenDTO accessToken = jwtTokenProvider.createAccessToken(user.getEmail());

        // login 시 Redis에 RT:bareuni@email.com(key): --refresh token실제값--(value) 형태로 refresh 토큰 저장하기
        // opsForValue() : set을 통해 key,value값 저장하고 get(key)통해 value가져올 수 있음.
        // refreshToken.getTokenExpriresTime().getTime() : 리프레시 토큰의 만료시간이 지나면 해당 값 자동 삭제
        redisTemplate.opsForValue().set("RT:"+user.getEmail(),refreshToken.getToken(),refreshToken.getTokenExpriresTime().getTime(),TimeUnit.MILLISECONDS);

        List<TokenDTO> tokenDTOList = new ArrayList<>();
        tokenDTOList.add(refreshToken);
        tokenDTOList.add(accessToken);
        System.out.println(tokenDTOList);

        return tokenDTOList;
    }

    // 로그아웃
    public String logout(HttpServletRequest httpServletRequest) throws BaseException {
        try {
            Long userIdx = jwtTokenProvider.getCurrentUser(httpServletRequest);
            System.out.println("getCurrentUser()로 가져온 userIdx : "+userIdx);
            User user = userRepository.findById(userIdx)
                    .orElseThrow(() -> new BaseException(INVALID_USER_JWT));

            // Redis 에서 해당 User email 로 저장된 Refresh Token 이 있는지 여부를 확인 후 있을 경우 삭제
            if (redisTemplate.opsForValue().get("RT:" + user.getEmail()) != null) {
                // Refresh Token 삭제
                redisTemplate.delete("RT:" + user.getEmail());
            }
            // 해당 AccessToken 유효시간 가지고 와서 BlackList 로 저장하기
            String accessToken = jwtTokenProvider.resolveAccessToken(httpServletRequest);
            Long expiration = jwtTokenProvider.getExpireTime(accessToken).getTime();
            // Redis 에 --accesstoken--(key) : logout(value) 로 저장, token 만료시간 지나면 자동 삭제
            redisTemplate.opsForValue().set(accessToken, "logout", expiration, TimeUnit.MILLISECONDS);

            return "로그아웃 성공";
        } catch (Exception e) {
            return "유효하지 않은 토큰입니다.";
        }
    }

    // 회원 탈퇴
    @Transactional
    public String deactivateUser(HttpServletRequest httpServletRequest) throws BaseException {
        //String email = jwtTokenProvider.getCurruntUserEmail(httpServletRequest);
        String accessToken = jwtTokenProvider.resolveAccessToken(httpServletRequest);
        String email = jwtTokenProvider.getUserPk(accessToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BaseException(USERS_EMPTY_USER_ID));// 유저아이디확인해주세요<-사용자 찾을수없습니다 추가하기

        //redis에 로그인되어있는 토큰 삭제
        // Redis 에서 해당 User email 로 저장된 Refresh Token 이 있는지 여부를 확인 후 있을 경우 삭제
        if (redisTemplate.opsForValue().get("RT:" + user.getEmail()) != null) {
            // Refresh Token 삭제
            redisTemplate.delete("RT:" + user.getEmail());
        }

        // 탈퇴한 토큰을 차단 (deactivateUser 토큰 블랙리스트)
        Long expiration = jwtTokenProvider.getExpireTime(accessToken).getTime();
        // Redis 에 --accesstoken--(key) : logout(value) 로 저장, token 만료시간 지나면 자동 삭제
        redisTemplate.opsForValue().set(accessToken, "logout", expiration, TimeUnit.MILLISECONDS);

        // 해당 회원삭제
        communityRepository.deleteAllByUser(user);
        likeRepository.deleteAllByUser(user);
        commentRepository.deleteAllByUser(user);
        bookingRepository.deleteAllByUser(user);
        scrapRepository.deleteAllByUser(user);
        reviewRepository.deleteAllByUser(user);
        userRepository.deleteById(user.getUserIdx());

        //시큐리티
        //SecurityContextHolder.clearContext();
        return "회원 탈퇴 성공";
    }
}
