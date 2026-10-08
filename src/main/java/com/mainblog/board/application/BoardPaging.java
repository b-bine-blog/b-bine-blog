package com.mainblog.board.application;

import com.mainblog.common.config.BoardProperties;
import com.mainblog.common.error.InvalidInputException;
import org.springframework.stereotype.Component;

/** 번호 페이지 값 확인 (FR-017, FR-057). 한 페이지 개수는 설정값(10·20·30)만 받는다. */
@Component
public class BoardPaging {

    private final BoardProperties props;

    public BoardPaging(BoardProperties props) {
        this.props = props;
    }

    public int page(Integer page) {
        if (page == null) {
            return 1;
        }
        if (page < 1) {
            throw new InvalidInputException("page", "Min");
        }
        return page;
    }

    public int size(Integer size) {
        if (size == null) {
            return props.list().defaultSize();
        }
        if (!props.list().pageSizes().contains(size)) {
            throw new InvalidInputException("size", "Allowed");
        }
        return size;
    }
}
