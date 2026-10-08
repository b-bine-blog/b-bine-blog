package com.mainblog.board.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.board.application.BoardViews.CategoryList;
import com.mainblog.board.application.BoardViews.CategoryView;
import com.mainblog.board.application.CategoryService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 카테고리 목록·추가·이름 바꾸기·삭제·순서 (004 T026, FR-021, FR-022, D-100 #8). 관리는 POST_MANAGE만. */
@RestController
public class CategoryController {

    public record CategoryRequest(String name) {}

    public record OrderRequest(List<Long> categoryIds) {}

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/api/blogs/{blogId}/categories")
    public CategoryList list(@PathVariable long blogId, @AuthenticationPrincipal AuthenticatedMember member) {
        return categoryService.list(blogId, member == null ? null : member.id());
    }

    @PostMapping("/api/blogs/{blogId}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView create(@PathVariable long blogId, @RequestBody CategoryRequest request) {
        return categoryService.create(blogId, request.name());
    }

    @PutMapping("/api/blogs/{blogId}/categories/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@PathVariable long blogId, @RequestBody OrderRequest request) {
        categoryService.reorder(blogId, request.categoryIds());
    }

    @PatchMapping("/api/blogs/{blogId}/categories/{categoryId}")
    public CategoryView rename(@PathVariable long blogId, @PathVariable long categoryId,
                               @RequestBody CategoryRequest request) {
        return categoryService.rename(blogId, categoryId, request.name());
    }

    @DeleteMapping("/api/blogs/{blogId}/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long blogId, @PathVariable long categoryId) {
        categoryService.delete(blogId, categoryId);
    }
}
