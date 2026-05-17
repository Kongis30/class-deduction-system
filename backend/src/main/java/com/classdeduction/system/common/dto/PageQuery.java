package com.classdeduction.system.common.dto;

/**
 * Legacy shared page query object.
 * New modules should extend the newer common model variant instead of this transitional DTO.
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public class PageQuery {

    private long pageNum = 1;
    private long pageSize = 10;

    public long getPageNum() {
        return pageNum;
    }

    public void setPageNum(long pageNum) {
        this.pageNum = pageNum;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }
}
