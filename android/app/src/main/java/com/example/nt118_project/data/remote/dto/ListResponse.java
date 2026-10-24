package com.example.nt118_project.data.remote.dto;

import java.util.ArrayList;
import java.util.List;

/** Danh sách có phân trang của server: {rows, total, page, limit}. Một số API chỉ trả rows. */
public class ListResponse<T> {
    public List<T> rows = new ArrayList<>();
    public int total;
    public int page;
    public int limit;
}
