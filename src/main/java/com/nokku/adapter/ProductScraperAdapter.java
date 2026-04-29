package com.nokku.adapter;


import com.nokku.model.Product;
import java.util.List;

public interface ProductScraperAdapter {

    String getType();   // generic, json, api

    List<Product> search(String query, String source);
}