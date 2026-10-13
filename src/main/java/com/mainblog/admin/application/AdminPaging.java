package com.mainblog.admin.application;

import com.mainblog.common.config.AdminProperties;
import com.mainblog.common.error.InvalidInputException;
import org.springframework.stereotype.Component;

/** 관리자 목록 페이지 값 (005 T008, contracts Page·Size). 개수는 10·20·50만(기본 20), 다른 값은 400 VALIDATION_FAILED. */
@Component
public class AdminPaging {

    private final AdminProperties props;

    public AdminPaging(AdminProperties props) {
        this.props = props;
    }

    /** 1부터. 비우면 1, 1보다 작으면 400. */
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
            throw new InvalidInputException("size", "Pattern");
        }
        return size;
    }

    public static long offset(int page, int size) {
        return (long) (page - 1) * size;
    }
}
