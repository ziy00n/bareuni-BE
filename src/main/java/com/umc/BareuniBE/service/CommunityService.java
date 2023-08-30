package com.umc.BareuniBE.service;

import com.umc.BareuniBE.config.security.JwtTokenProvider;
import com.umc.BareuniBE.dto.CommunityReq;
import com.umc.BareuniBE.dto.CommunityRes;
import com.umc.BareuniBE.dto.UserRes;
import com.umc.BareuniBE.entities.Comment;
import com.umc.BareuniBE.entities.Community;
import com.umc.BareuniBE.entities.LikeEntity;
import com.umc.BareuniBE.entities.User;
import com.umc.BareuniBE.global.BaseException;
import com.umc.BareuniBE.repository.CommentRepository;
import com.umc.BareuniBE.repository.CommunityRepository;
import com.umc.BareuniBE.repository.LikeRepository;
import com.umc.BareuniBE.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import static com.umc.BareuniBE.global.BaseResponseStatus.USERS_EMPTY_USER_ID;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.umc.BareuniBE.global.BaseResponseStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class  CommunityService {

    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final JwtTokenProvider jwtTokenProvider;


    public CommunityRes.CommunityCreateRes createCommunity(CommunityReq.CommunityCreateReq communityCreateReq, HttpServletRequest request) throws BaseException {
        log.info(String.valueOf(jwtTokenProvider.getCurrentUser(request)));
        User user = userRepository.findById(jwtTokenProvider.getCurrentUser(request))
                .orElseThrow(() ->  new BaseException(USERS_EMPTY_USER_ID));

        Community newCommunity = Community.builder()
                .user(user)
                .content(communityCreateReq.getContent())
                .build();

        return new CommunityRes.CommunityCreateRes(communityRepository.saveAndFlush(newCommunity));
    }

    public List<CommunityRes.CommunityListRes> getCommunityList(Pageable page) {
        List<Object[]> communities = communityRepository.findAllCommunity_Pagination(PageRequest.of(page.getPageNumber(), page.getPageSize(), page.getSort()));

        return communities.stream()
                .map(communityData -> {
                    CommunityRes.CommunityListRes communityRes = new CommunityRes.CommunityListRes();
                    communityRes.setCommunityIdx(communityData[0]);
                    communityRes.setCreatedAt(communityData[1]);
                    communityRes.setUpdatedAt(communityData[2]);
                    communityRes.setContent(communityData[3]);
                    communityRes.setUser(userRepository.findById(((BigInteger)communityData[4]).longValue()).orElse(null));
                    communityRes.setLike(communityData[5]);

                    return communityRes;
                })
                .collect(Collectors.toList());
    }

    public CommunityRes.CommunityDetailRes getCommunityDetails(Long communityIdx) throws BaseException {
        Community community = communityRepository.findById(communityIdx)
                .orElseThrow(() -> new BaseException(COMMUNITY_EMPTY_ID));

        List<Comment> comments = commentRepository.findAllByCommunity(community);
        List<CommunityRes.CommentSummary> commentList = comments.stream()
                .map(comment -> {
                    CommunityRes.CommentSummary commentSummary = new CommunityRes.CommentSummary();
                    commentSummary.setNickname(comment.getUser().getNickname());
                    commentSummary.setComment(comment.getComment());
                    commentSummary.setCommentCreatedAt(comment.getCreatedAt());
                    return commentSummary;
                })
                .collect(Collectors.toList());
        return new CommunityRes.CommunityDetailRes(community.getCommunityIdx(), community.getUser(), community.getContent(), commentList);
    }

//    public CommunityRes.CommunityCreateRes updateCommunity(Long communityIdx, CommunityReq.CommunityCreateReq request) throws BaseException {
//        // 해당 글 유저
//        Community community = communityRepository.findById(communityIdx)
//                .orElseThrow(() -> new BaseException(COMMUNITY_EMPTY_ID));
//        // request 로 받은 유저
//        User user = userRepository.findById(request.getUserIdx())
//                .orElseThrow(() ->  new BaseException(USERS_EMPTY_USER_ID));
//
//
//        if (community.getUser() != user)
//            throw new BaseException(UPDATE_AUTHORIZED_ERROR);
//
//
//        community.setContent(request.getContent());
//        community.setUpdatedAt(LocalDateTime.now());
//
//        return new CommunityRes.CommunityCreateRes(communityRepository.saveAndFlush(community));
//    }

    public String deleteCommunity(Long communityIdx, Long userIdx) throws BaseException {
        // 해당 글 유저
        Community community = communityRepository.findById(communityIdx)
                .orElseThrow(() -> new BaseException(COMMUNITY_EMPTY_ID));
        // request 로 받은 유저
        User user = userRepository.findById(userIdx)
                .orElseThrow(() ->  new BaseException(USERS_EMPTY_USER_ID));


        if (community.getUser() != user)
            throw new BaseException(UPDATE_AUTHORIZED_ERROR);

        communityRepository.delete(community);
        return "삭제 성공";
    }

    public String likeToggle(Long userIdx, Long communityIdx) throws BaseException {

        User user = userRepository.findById(userIdx)
                .orElseThrow(() ->  new BaseException(USERS_EMPTY_USER_ID));
        Community community = communityRepository.findById(communityIdx)
                .orElseThrow(() -> new BaseException(COMMUNITY_EMPTY_ID));


        Optional<LikeEntity> likeRelation = likeRepository.findByUserAndCommunity(user, community);

        if (likeRelation.isPresent()) {
            likeRepository.delete(likeRelation.get());
            return "좋아요 취소";
        }
        else {
            likeRepository.saveAndFlush(new LikeEntity(user, community));
            return "좋아요 성공";
        }

    }

    public CommunityRes.CommentCreateRes createComment (Long communityIdx, CommunityReq.CommentCreateReq request) throws BaseException {
        User user = userRepository.findById(request.getUserIdx())
                .orElseThrow(() ->  new BaseException(USERS_EMPTY_USER_ID));

        Community community = communityRepository.findById(communityIdx)
                .orElseThrow(() -> new BaseException(COMMUNITY_EMPTY_ID));

        Comment newComment = Comment.builder()
                .user(user)
                .community(community)
                .comment(request.getComment())
                .build();

        return new CommunityRes.CommentCreateRes(commentRepository.saveAndFlush(newComment));
    }

    public String deleteComment (Long commentIdx, CommunityReq.CommentDeleteReq request) throws BaseException {
        User user = userRepository.findById(request.getUserIdx())
                .orElseThrow(() ->  new BaseException(USERS_EMPTY_USER_ID));

        Comment comment = commentRepository.findById(commentIdx)
                .orElseThrow(() -> new BaseException(Comment_EMPTY_ID));

        if (comment.getUser() != user)
            throw new BaseException(UPDATE_AUTHORIZED_ERROR);

        commentRepository.delete(comment);

        return "댓글 삭제 성공!";
    }

    public List<CommunityRes.BestCommunityListRes> getBestCommunityList() {
        List<Object[]> communities = communityRepository.getBestCommunityList();

        return communities.stream()
                .map(communityData -> {
                    CommunityRes.BestCommunityListRes bestCommunityListRes = new CommunityRes.BestCommunityListRes();
                    bestCommunityListRes.setCommunityIdx(communityData[0]);
                    bestCommunityListRes.setCreatedAt(communityData[1]);
                    bestCommunityListRes.setUpdatedAt(communityData[2]);
                    bestCommunityListRes.setContent(communityData[3]);
                    bestCommunityListRes.setLikeCnt(communityData[5]);

                    return bestCommunityListRes;
                })
                .collect(Collectors.toList());
    }

    public List<CommunityRes.CommunityListRes> searchCommunity(String keyword) throws BaseException {
        List<CommunityRes.CommunityListRes> communityList = communityRepository.searchCommunity(keyword);

        if (communityList.isEmpty()) {
            throw new BaseException(EMPTY_SEARCH_KEYWORD);
        }

        return communityList;
    }
}
