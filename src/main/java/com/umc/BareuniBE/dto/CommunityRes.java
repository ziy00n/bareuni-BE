package com.umc.BareuniBE.dto;

import com.umc.BareuniBE.entities.Comment;
import com.umc.BareuniBE.entities.Community;
import com.umc.BareuniBE.entities.User;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class CommunityRes {
//    @Builder
//    @NoArgsConstructor
//    @AllArgsConstructor
    @Getter
    @Setter
    public static class CommunityCreateRes {
        private Long communityIdx;

        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String content;
        private UserRes.UserSummary user;

    public CommunityCreateRes(Community community) {
        this.communityIdx = community.getCommunityIdx();
        this.createdAt = community.getCreatedAt();
        this.updatedAt = community.getUpdatedAt();
        this.content = community.getContent();
        this.user = new UserRes.UserSummary(community.getUser());
    }
}

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class CommunityListRes {


        private Object communityIdx;

        private Object createdAt;

        private Object updatedAt;

        private User user;

        private Object content;
        private Object like;

    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class CommunityDetailRes {


        private Long communityIdx;

        private User user;
       
        private String content;
      
        private List<CommentSummary> commentList;


    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class CommentSummary {

        private String nickname;
        private String comment;
        private LocalDateTime commentCreatedAt;

    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class CommentCreateRes {
        private Comment comment;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class BestCommunityListRes {
        private Object communityIdx;
        private Object createdAt;
        private Object updatedAt;
        private Object content;
        private Object likeCnt;
    }
}

