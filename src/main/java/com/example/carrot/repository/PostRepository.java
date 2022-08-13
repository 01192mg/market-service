package com.example.carrot.repository;

import com.example.carrot.model.Member;
import com.example.carrot.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long>{

    List<Post> findAllByTitleContaining(String title);
    Optional<Post> findByMemberAndId(Member member, Long postId);
}
