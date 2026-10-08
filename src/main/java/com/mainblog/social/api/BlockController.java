package com.mainblog.social.api;

import com.mainblog.auth.security.AuthenticatedMember;
import com.mainblog.social.application.BlockService;
import com.mainblog.social.application.BlockService.BlockPage;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 차단·해제·내 차단 목록 (003 T042, SOC-05, contracts/social-api.yaml block). 로그인 필요. */
@RestController
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @PostMapping("/api/users/{userId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@PathVariable long userId, @AuthenticationPrincipal AuthenticatedMember member) {
        blockService.block(member.id(), userId);
    }

    @DeleteMapping("/api/users/{userId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable long userId, @AuthenticationPrincipal AuthenticatedMember member) {
        blockService.unblock(member.id(), userId);
    }

    @GetMapping("/api/me/blocks")
    public BlockPage blocks(@RequestParam(required = false) Integer page,
                            @AuthenticationPrincipal AuthenticatedMember member) {
        return blockService.blocks(member.id(), page);
    }
}
