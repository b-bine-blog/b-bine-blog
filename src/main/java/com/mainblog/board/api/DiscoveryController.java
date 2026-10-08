package com.mainblog.board.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.board.application.BoardViews.FeedResult;
import com.mainblog.board.application.BoardViews.PostPage;
import com.mainblog.board.application.BoardViews.RecentSearch;
import com.mainblog.board.application.BoardViews.SearchResult;
import com.mainblog.board.application.FeedService;
import com.mainblog.board.application.PostListService;
import com.mainblog.board.application.RecentSearchService;
import com.mainblog.board.application.SearchService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 메인 피드·통합 검색·최근 검색어·태그 글 목록 (004 T030·T043·T044, FR-023, FR-052~FR-064). */
@RestController
public class DiscoveryController {

    private final FeedService feedService;
    private final SearchService searchService;
    private final RecentSearchService recentSearchService;
    private final PostListService listService;

    public DiscoveryController(FeedService feedService, SearchService searchService,
                               RecentSearchService recentSearchService, PostListService listService) {
        this.feedService = feedService;
        this.searchService = searchService;
        this.recentSearchService = recentSearchService;
        this.listService = listService;
    }

    @GetMapping("/api/feed")
    public FeedResult feed(@RequestParam(required = false) String tab, @RequestParam(required = false) Integer page,
                           @RequestParam(required = false) Integer size, @RequestParam(required = false) String cursor,
                           @AuthenticationPrincipal AuthenticatedMember member) {
        return feedService.feed(tab, idOf(member), page, size, cursor);
    }

    @GetMapping("/api/search")
    public SearchResult search(@RequestParam(required = false) String q, @RequestParam(required = false) String type,
                               @RequestParam(required = false) String sort,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size,
                               @AuthenticationPrincipal AuthenticatedMember member) {
        return searchService.search(q, type, sort, page, size, idOf(member));
    }

    @GetMapping("/api/tags/{name}/posts")
    public PostPage tagPosts(@PathVariable String name, @RequestParam(required = false) Integer page,
                             @RequestParam(required = false) Integer size,
                             @AuthenticationPrincipal AuthenticatedMember member) {
        return listService.tagPosts(name, idOf(member), page, size);
    }

    @GetMapping("/api/me/recent-searches")
    public List<RecentSearch> recentSearches(@AuthenticationPrincipal AuthenticatedMember member) {
        return recentSearchService.list(member.id());
    }

    @DeleteMapping("/api/me/recent-searches")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAllRecentSearches(@AuthenticationPrincipal AuthenticatedMember member) {
        recentSearchService.deleteAll(member.id());
    }

    @DeleteMapping("/api/me/recent-searches/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecentSearch(@PathVariable long id, @AuthenticationPrincipal AuthenticatedMember member) {
        recentSearchService.delete(member.id(), id);
    }

    private static Long idOf(AuthenticatedMember member) {
        return member == null ? null : member.id();
    }
}
