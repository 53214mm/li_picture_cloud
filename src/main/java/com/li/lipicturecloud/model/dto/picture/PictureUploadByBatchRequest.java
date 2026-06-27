package com.li.lipicturecloud.model.dto.picture;

import lombok.Data;

@Data
public class PictureUploadByBatchRequest {  
  
    /**  
     * 搜索词  
     */  
    private String searchText;  
  
    /**  
     * 抓取数量  
     */  
    private Integer count = 10;

    /**
     * 名称前缀
     */
    private String namePrefix;

    /**
     * 偏移量（从第几张开始抓取，默认 0 表示从第一张开始）
     */
    private Integer offset = 0;

}
