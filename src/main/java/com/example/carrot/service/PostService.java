package com.example.carrot.service;

import com.example.carrot.image.S3UploaderService;
import com.example.carrot.jwt.TokenProvider;
import com.example.carrot.model.Bookmark;
import com.example.carrot.model.Member;
import com.example.carrot.model.Post;
import com.example.carrot.repository.BookmarkRepository;
import com.example.carrot.repository.MemberRepository;
import com.example.carrot.repository.PostRepository;
import com.example.carrot.request.PostRequestDto;
import com.example.carrot.response.BookmarkResponseDto;
import com.example.carrot.response.PostResponseDto;
import com.example.carrot.response.ResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final S3UploaderService s3UploaderService;
    private final MemberRepository memberRepository;
    private final TokenProvider tokenProvider;
    private final BookmarkRepository bookmarkRepository;


    public ResponseDto<?> getPostById(Long postId) {
        Post post = isPresentPost(postId);
        if (post == null) {
            ResponseDto.fail("POST_NOT_FOUND", "게시글이 존재하지 않습니다");
        }

        return ResponseDto.success(
                PostResponseDto.builder()
                        .id(post.getId())
                        .title(post.getTitle())
                        .content(post.getContent())
                        .bookmarks(post.getBookmark())
                        .nickname(post.getNickname())
                        .imageUrl(post.getImage_url())
                        .category(post.getCategory())
                        .price(post.getPrice())
                        .build()
        );
    }

    @Transactional
    public ResponseDto<?> createPost(PostRequestDto requestDto, HttpServletRequest request) throws IOException {

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

        // 멤버를 가지고, 게시글 만들기
        String imageUrl = s3UploaderService.upload(requestDto.getFile(), "static");

        Post post = Post.builder()
                .member(member)
                .title(requestDto.getTitle())
                .content(requestDto.getContent())
                .price(requestDto.getPrice())
                .nickname(member.getNickname())
                .image_url(imageUrl)
                .category(requestDto.getCategory())
                .build();

        postRepository.save(post);

        return ResponseDto.success(post);
    }

    public ResponseDto<?> getAllPosts(UserDetails userInfo) {
        List<Post> posts = postRepository.findAllByOrderByCreatedAtDesc();
        List<PostResponseDto> postResponseDtoList = new ArrayList<>();

        for(Post post : posts) {
            postResponseDtoList.add(PostResponseDto.builder()
                    .id(post.getId())
                    .title(post.getTitle())
                    .content(post.getContent())
                    .imageUrl(post.getImage_url())
                    .category(post.getCategory())
                    .price(post.getPrice())
                    .nickname(post.getNickname())
                    .modifiedAt(post.getModifiedAt())
                    .createdAt(post.getCreatedAt())
                    .flag(false)
                    .build()
            );
        }

        if(userInfo == null) {

            return ResponseDto.success(postResponseDtoList);

        } else {
            Member member = memberRepository.findByUsername(userInfo.getUsername()).orElse(null);
            List<Bookmark> bookmarkList = bookmarkRepository.findAllByMember(member);

            for(Bookmark bookmark: bookmarkList) {
                for(PostResponseDto dto : postResponseDtoList) {
                    if(bookmark.getPost().getId().equals(dto.getId())  ) {
                        dto.setFlag(true);
                        break;
                    }
                }
            }

            return ResponseDto.success(postResponseDtoList);


        }



    }

    @Transactional
    public ResponseDto<?> updatePost(Long postId, PostRequestDto requestDto, HttpServletRequest request) throws IOException {

        // 멤버를 가지고 오기
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

        Post post = isPresentPost(postId);
        if (null == post) {
            return ResponseDto.fail("NOT_FOUND", "존재하지 않는 게시글 id 입니다.");
        }

        if (post.validateMember(member)) {
            return ResponseDto.fail("BAD_REQUEST", "작성자만 수정할 수 있습니다.");
        }

        String imageUrl = post.getImage_url();

        if (requestDto.getFile() != null) {
            String deleteUrl = imageUrl.substring(imageUrl.indexOf("static"));
            s3UploaderService.deleteImage(deleteUrl);

            imageUrl = s3UploaderService.upload(requestDto.getFile(), "static");
        }

        post.update(imageUrl, requestDto);
        return ResponseDto.success(post);
    }

    @Transactional
    public ResponseDto<?> deletePost(Long postId, HttpServletRequest request) {

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

        Post post = isPresentPost(postId);
        if (null == post) {
            return ResponseDto.fail("NOT_FOUND", "존재하지 않는 게시글 id 입니다.");
        }

        if (post.validateMember(member)) {
            return ResponseDto.fail("BAD_REQUEST", "작성자만 삭제할 수 있습니다.");
        }

        String imageUrl = post.getImage_url();
        String deleteUrl = imageUrl.substring(imageUrl.indexOf("static"));

        s3UploaderService.deleteImage(deleteUrl);
        postRepository.delete(post);
        return ResponseDto.success("delete success");
    }


    // 후에는 정규표현식 추가
    public ResponseDto<?> searchPost(String searchKeyword) {
        if (searchKeyword == null) {
            return ResponseDto.fail("DATA_NOT_FOUND", "글자를 입력해 주세요");
        }
        List<Post> findPosts = postRepository.findAllByTitleContaining(searchKeyword);
        if (findPosts == null) {
            return ResponseDto.fail("SEARCH_NOT_FOUND", "게시글을 찾을 수 없습니다");
        }
        return ResponseDto.success(findPosts);
    }

    @Transactional(readOnly = true)
    public Post isPresentPost(Long postId) {
        Optional<Post> optionalPost = postRepository.findById(postId);
        return optionalPost.orElse(null);
    }

    @Transactional
    public Member validateMember(HttpServletRequest request) {
        if (!tokenProvider.validateToken(request.getHeader("Refresh-Token"))) {
            return null;
        }
        return tokenProvider.getMemberFromAuthentication();
    }

    public ResponseDto<?> getPostsByCategory(String category) {

        List<Post> allPostsByCategory = postRepository.findAllByCategory(category);
        return ResponseDto.success(allPostsByCategory);
    }

 /*   public ResponseDto<?> getPostsByBookmark(HttpServletRequest request) {
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
        // 북마크 중,
        List<Bookmark> bookmarks = bookmarkRepository.findAllByMember(member);
    }*/

    @Transactional
    public ResponseDto<?> flagBookmark(Long postId, String username) {
        Post post = postRepository.findById(postId).orElse(null);
        Member member = memberRepository.findByUsername(username).orElse(null);
        if(post == null) {
            return ResponseDto.fail("NOT_EXIST_POST", "존재하지 않는 게시글입니다.");
        }
        if(member == null) {
            return ResponseDto.fail("NOT_EXIST_Member", "존재하지 않는 회원입니다.");
        }

        Bookmark bookmark = bookmarkRepository.findByPostAndMember(post, member).orElse(null);
        String state = "";

        if(bookmark == null) {
            bookmark = new Bookmark(post,member);
            bookmarkRepository.save(bookmark);
            state = "북마크 추가";
        } else {
           bookmarkRepository.deleteByPostAndMember(post,member);
            state = "북마크 삭제";
        }

        BookmarkResponseDto bookmarkResponseDto = new BookmarkResponseDto(bookmark, state);

        return ResponseDto.success(bookmarkResponseDto) ;
    }
}
