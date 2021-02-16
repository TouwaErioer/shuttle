package com.example.hope.common.provider;

import java.util.Map;

/**
 * @description: SQL公共提供类
 * @author: DHY
 * @created: 2021/02/08 13:39
 */
public class Provider {

    public static String selectByKey(Map<String, Object> para, String sql, String table) {
        StringBuilder stringBuffer = new StringBuilder();
        stringBuffer.append(sql);
        String orderBy = String.format(" order by %s.id desc", table);
        if (para.get("key") != null && para.get("id") != null) {
            if (para.get("key").equals("search"))
                stringBuffer.append(" where ").append(table).append(".name like '%").append(para.get("id"))
                        .append("%'").append(orderBy);
            else if (table.equals("orders"))
                stringBuffer.append(" and ").append(table).append(".").append(para.get("key")).append(" = ")
                        .append(para.get("id")).append(orderBy);
            else
                stringBuffer.append(" where ").append(table).append(".").append(para.get("key")).append(" = ")
                        .append(para.get("id")).append(orderBy);
        }else stringBuffer.append(orderBy);
        return stringBuffer.toString();
    }
}
