package com.example.carrot.service;

import com.example.carrot.jwt.TokenProvider;
import com.example.carrot.model.Member;
import com.example.carrot.repository.CommentRepository;
import com.example.carrot.repository.MemberRepository;
import com.example.carrot.repository.PostRepository;
import com.example.carrot.request.LoginRequestDto;
import com.example.carrot.request.MemberRequestDto;
import com.example.carrot.request.TokenDto;
import com.example.carrot.response.MemberInfoResponseDto;
import com.example.carrot.response.MemberResponseDto;
import com.example.carrot.response.ResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class MemberService {

  private final MemberRepository memberRepository;

  private final PasswordEncoder passwordEncoder;
//  private final AuthenticationManagerBuilder authenticationManagerBuilder;
  private final TokenProvider tokenProvider;

  private final PostRepository postRepository;
  private final CommentRepository commentRepository;

  @Transactional
  public ResponseDto<?> createMember(MemberRequestDto requestDto) {
    if (null != isPresentMember(requestDto.getUsername())) {
      return ResponseDto.fail("DUPLICATED_USERNAME",
          "중복된 아이디 입니다.");
    }

    Member member = Member.builder()
            .username(requestDto.getUsername())
            .nickname(requestDto.getNickname())
                .password(passwordEncoder.encode(requestDto.getPassword()))
                    .build();
    memberRepository.save(member);
    return ResponseDto.success(
        MemberResponseDto.builder()
            .id(member.getMember_id())
            .username(member.getUsername())
            .nickname(member.getNickname())
            .createdAt(member.getCreatedAt())
            .modifiedAt(member.getModifiedAt())
            .build()
    );
  }

  @Transactional
  public ResponseDto<?> login(LoginRequestDto requestDto, HttpServletResponse response) {
    Member member = isPresentMember(requestDto.getUsername());
    if (null == member) {
      return ResponseDto.fail("MEMBER_NOT_FOUND",
          "사용자를 찾을 수 없습니다.");
    }

    if (!member.validatePassword(passwordEncoder, requestDto.getPassword())) {
      return ResponseDto.fail("INVALID_MEMBER", "사용자를 찾을 수 없습니다.");
    }

    TokenDto tokenDto = tokenProvider.generateTokenDto(member);
    tokenToHeaders(tokenDto, response);

    return ResponseDto.success(
        MemberResponseDto.builder()
            .id(member.getMember_id())
            .nickname(member.getNickname())
            .createdAt(member.getCreatedAt())
            .modifiedAt(member.getModifiedAt())
            .build()
    );
  }

  public ResponseDto<?> logout(HttpServletRequest request) {
    if (!tokenProvider.validateToken(request.getHeader("Refresh-Token"))) {
      return ResponseDto.fail("INVALID_TOKEN", "Token이 유효하지 않습니다.");
    }
    Member member = tokenProvider.getMemberFromAuthentication();
    if (null == member) {
      return ResponseDto.fail("MEMBER_NOT_FOUND",
          "사용자를 찾을 수 없습니다.");
    }

    return tokenProvider.deleteRefreshToken(member);
  }

  @Transactional(readOnly = true)
  public Member isPresentMember(String username) {
    Optional<Member> optionalMember = memberRepository.findByUsername(username);
    return optionalMember.orElse(null);
  }

  public void tokenToHeaders(TokenDto tokenDto, HttpServletResponse response) {
    response.addHeader("Authorization", "Bearer " + tokenDto.getAccessToken());
    response.addHeader("Refresh-Token", tokenDto.getRefreshToken());
    response.addHeader("Access-Token-Expire-Time", tokenDto.getAccessTokenExpiresIn().toString());
  }
/*
  @Transactional
  public ResponseDto<?> mypage(HttpServletRequest request) {
    if (null == request.getHeader("Refresh-Token")) {
      return ResponseDto.fail("MEMBER_NOT_FOUND",
              "로그인이 필요합니다.");
    }

    if (null == request.getHeader("Authorization")) {
      return ResponseDto.fail("MEMBER_NOT_FOUND",
              "로그인이 필요합니다.");
    }

    Member member = validateMember(request);
    if (null == member) {
      return ResponseDto.fail("INVALID_TOKEN", "Token이 유효하지 않습니다.");
    }

    List<Post> postsByMember = postRepository.findAllByMember(member);
    List<Comment> commentsByMember = commentRepository.findAllByMember(member);

    // 좋아요한 자료들
    List<PostHeart> heartsByMember = postHeartRepository.findAllByMember(member);

    return ResponseDto.success(
            MypageResponseDto.builder()
                    .posts(postsByMember)
                    .comments(commentsByMember)
                    .replies(repliesByMember)
                    .build(),
            MyHeartResponseDto.builder()
                    .postHearts(heartsByMember)
                    .build()
    );
  }*/

  @Transactional
  public Member validateMember(HttpServletRequest request) {
    if (!tokenProvider.validateToken(request.getHeader("Refresh-Token"))) {
      return null;
    }
    return tokenProvider.getMemberFromAuthentication();
  }

  public ResponseDto<?> checkId(String username) {
    Member member = memberRepository.findByUsername(username).orElse(null);

    if(member != null) {
      return ResponseDto.fail("FAIL_ID_DUPLICATE","아이디가 중복됩니다.");
    }
    return ResponseDto.success(username);
  }

  public ResponseDto<?> LoginInfo(UserDetails userInfo) {
    Member member = memberRepository.findByUsername(userInfo.getUsername()).orElseThrow(
            () -> new IllegalArgumentException("로그인 상태가 아닙니다.")
    );

    MemberInfoResponseDto memberInfoResponseDto = MemberInfoResponseDto.builder()
            .username(member.getUsername())
            .nickname(member.getNickname())
            .build();

    return ResponseDto.success(memberInfoResponseDto);

  }

}
