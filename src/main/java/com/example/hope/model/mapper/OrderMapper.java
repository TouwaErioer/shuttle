package com.example.hope.model.mapper;

import com.example.hope.model.entity.Order;
import com.example.hope.model.entity.OrderDetail;
import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Mapper
public interface OrderMapper {

    @Insert("insert into orders(cid,uid,pid,create_time,address,note,file_url,complete,order_status) values(#{cid},#{uid},#{pid},#{create_time},#{address},#{note},#{file_url},#{complete},#{order_status})")
    int insert(Order order);

    @Delete("delete from orders where id = #{id}")
    int delete(long id);

    @Update("update orders set address = #{address},note = #{note},file_url = #{file_url} where id = #{id}")
    int update(Order order);

    @SelectProvider(type = OrderProvider.class,method = "choose")
    List<OrderDetail> findAll(@Param("idName") String idName,@Param("sort") String sort,@Param("order") String order,@Param("completed") String completed,@Param("received") String received);

    @SelectProvider(type = OrderProvider.class,method = "choose")
    List<OrderDetail> findByPid(long pid,@Param("idName") String idName,@Param("sort") String sort,@Param("order") String order,@Param("completed") String completed,@Param("received") String received);

    @SelectProvider(type = OrderProvider.class,method = "choose")
    List<OrderDetail> findByCid(long cid,@Param("idName") String idName,@Param("sort") String sort,@Param("order") String order,@Param("completed") String completed,@Param("received") String received);

    @SelectProvider(type = OrderProvider.class,method = "choose")
    List<OrderDetail> findByUid(long uid,@Param("idName") String idName,@Param("sort") String sort,@Param("order") String order,@Param("completed") String completed,@Param("received") String received);

    @SelectProvider(type = OrderProvider.class,method = "choose")
    List<OrderDetail> findByType(long sid,@Param("idName") String idName,@Param("sort") String sort,@Param("order") String order,@Param("completed") String completed,@Param("received") String received);

    @Update("update orders set order_status = 1 where id = #{id}")
    int receive(long id);

    @Update("update orders set completed = 1 where id = #{id}")
    int completed(long id);

    class OrderProvider{

        String sql = "select " +
                "c.id,cid,uid,pid," +
                "e.id as sid," +
                "a.username as user," +
                "b.username as waiter," +
                "d.product_name as product," +
                "e.service_name as type," +
                "create_time,c.address,note,file_url,complete,order_status " +
                "from user as a,user as b,orders as c,product as d,service as e " +
                "where c.cid = a.id " +
                "and c.uid = b.id " +
                "and c.pid = d.id " +
                "and d.service_type = e.id";

        String completed_sql = " and complete = 1";

        String notCompleted_sql = " and complete = 0";

        String received_sql = " and order_status = 1";

        String notReceived_sql = " and order_status = 0";

        String pid = " and c.pid = #{pid}";

        String cid = " and c.cid = #{cid}";

        String uid = " and c.uid = #{uid}";

        String sid = " and c.sid = #{sid}";

        String create_time = " order by create_time ";

        // 正序
        String ASC = "ASC";

        // 倒序
        String DESC ="DESC";

        public String choose(Map<String,Object> para) {

            if(para.get("completed") != null){
                switch (para.get("completed").toString()){
                    case "1":
                        sql = sql + completed_sql;
                        break;
                    case "0":
                        sql = sql + notCompleted_sql;
                        break;
                    case " ":
                        break;
                }
            }

            if(para.get("received") != null){
                switch (para.get("received").toString()){
                    case "1":
                        sql = sql + received_sql;
                        break;
                    case "0":
                        sql = sql + notReceived_sql;
                        break;
                    case " ":
                        break;

                }
            }

            if(para.get("idName") != null) {
                switch (para.get("idName").toString()) {
                    case "pid":
                        sql = sql + pid;
                        break;
                    case "cid":
                        sql = sql + cid;
                        break;
                    case "uid":
                        sql = sql + uid;
                        break;
                    case "sid":
                        sql = sql + sid;
                        break;
                }
            }

            if(para.get("sort") != null) {
                switch (para.get("sort").toString()) {
                    case "create_time":
                        sql = sql + create_time;
                        break;
                }
            }

            if(para.get("order") != null) {
                switch (para.get("order").toString()) {
                    case "ASC":
                        sql = sql + ASC;
                        break;
                    case "DESC":
                        sql = sql + DESC;
                        break;
                }
            }
             return sql;
        }
    }
}