package com.example.hope.service.serviceIpm;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hope.base.service.imp.BaseServiceImp;
import com.example.hope.common.utils.JwtUtils;
import com.example.hope.common.utils.PageUtils;
import com.example.hope.common.utils.Utils;
import com.example.hope.config.exception.BusinessException;
import com.example.hope.config.redis.RedisService;
import com.example.hope.model.bo.Query;
import com.example.hope.model.entity.Orders;
import com.example.hope.model.entity.Store;
import com.example.hope.model.mapper.StoreMapper;
import com.example.hope.model.vo.OrdersVO;
import com.example.hope.model.vo.StoreVO;
import com.example.hope.repository.elasticsearch.EsPageHelper;
import com.example.hope.repository.elasticsearch.StoreRepository;
import com.example.hope.service.business.*;
import lombok.extern.log4j.Log4j2;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

/**
 * @description: 商店服务实现类
 * @author: DHY
 * @created: 2021/02/03 19:48
 */

@Service
@Log4j2
public class StoreServiceImp extends BaseServiceImp<Store, StoreMapper> implements StoreService {

    @Resource
    private StoreMapper storeMapper;

    @Resource
    private RedisService redisService;

    @Resource
    private ProductService productService;

    @Resource
    private StoreRepository storeRepository;

    @Resource
    private CategoryService categoryService;

    @Resource
    private BusinessService businessService;

    @Resource
    private EsPageHelper<Store> esPageHelper;

    @Resource
    private OrderService orderService;

    /**
     * 添加商店
     *
     * @param store 商店
     */
    @Override
    @Transactional
    @CacheEvict(value = "store", allEntries = true)
    public boolean insert(Store store) {
        boolean status = !categoryService.exist(store.getCategoryId()) || !businessService.exist(store.getBusinessId());
        BusinessException.check(status, "类别或业务id不存在");
        storeRepository.save(store);
        return this.save(store);
    }

    /**
     * 增加商店销量
     *
     * @param id       商店id
     * @param quantity 数量
     */
    @Override
    public boolean sales(long id, int quantity) {
        Store store = this.getById(id, "商店不存在");
        redisService.review(store.getRate(), store.getSales(), "store_rank", String.valueOf(id));
        store.setSales(store.getSales() + quantity);
        return this.updateById(store);
    }

    /**
     * 删除商店
     *
     * @param id 商店id
     */
    @Override
    @Transactional
    @CacheEvict(value = "store", allEntries = true)
    public boolean delete(long id) {
        productService.deleteByStoreId(id);
        storeRepository.deleteById(id);
        return this.removeById(id);
    }

    /**
     * 根据类别id删除商店
     *
     * @param categoryId 类别id
     */
    @Override
    @Transactional
    @CacheEvict(value = "store", allEntries = true)
    public boolean deleteByCategoryId(long categoryId) {
        boolean state = false;
        for (Store store : findByCategoryId(categoryId)) {
            state = productService.deleteByStoreId(store.getId());
        }
        Wrapper<Store> wrapper = new LambdaQueryWrapper<Store>()
                .eq(Store::getCategoryId, categoryId);
        return this.remove(wrapper) && state;
    }

    /**
     * 更新商店评分
     *
     * @param id    商店id
     * @param rate  评分
     * @param token Token
     */
    @Override
    @CacheEvict(value = "store", allEntries = true)
    public boolean review(long id, float rate, String token) {
        long userId = JwtUtils.getUserId(token);
        List<OrdersVO> list = orderService.findByCid(userId);
        boolean status = false;
        for (OrdersVO orders : list) {
            if (orders.getStoreId() == id) {
                status = true;
                break;
            }
        }
        if (!JwtUtils.is_admin(token) || !status) throw new BusinessException(1, "只允许管理员或在此商店消费过的用户可以评分");
        Store store = this.getById(id, "商店不存在");
        store.setRate(Utils.composeScore(rate, store.getSales()));
        redisService.review(store.getRate(), store.getSales(), "store_rank", String.valueOf(id));
        return this.updateById(store);
    }

    /**
     * 更新商店
     *
     * @param store 商店
     */
    @Override
    @Transactional
    @CacheEvict(value = "store", allEntries = true)
    public boolean update(Store store) {
        boolean res = !categoryService.exist(store.getCategoryId()) || !businessService.exist(store.getBusinessId());
        BusinessException.check(res, "类别或业务id不存在");
        storeRepository.save(store);
        return this.updateById(store);
    }

    /**
     * 查询全部商店
     *
     * @return 分页包装类
     */
    @Override
    @Cacheable(value = "store", key = "methodName + #query.toString()")
    public IPage<StoreVO> page(Query query) {
        Page<StoreVO> page = PageUtils.getQuery(query);
        Wrapper<Store> wrapper = new QueryWrapper<>();
        return this.baseMapper.selectByPage(page, wrapper);
    }

    /**
     * 查询全部商店
     *
     * @return List<Store>
     */
    @Cacheable(value = "store", key = "methodName")
    public List<StoreVO> getList() {
        return this.baseMapper.findAll();
    }

    /**
     * 根据businessId查询商店
     *
     * @param businessId 业务id
     * @return 商店列表
     */
    @Override
    @Cacheable(value = "store", key = "methodName + #businessId + #query.toString()")
    public IPage<StoreVO> findByServiceId(long businessId, Query query) {
        Page<StoreVO> page = PageUtils.getQuery(query);
        Wrapper<Store> wrapper = new QueryWrapper<Store>()
                .eq("a.business_id", businessId);
        return this.baseMapper.selectByPage(page, wrapper);
    }

    /**
     * 根据categoryId查询商店
     *
     * @param categoryId 类别id
     * @return 商店列表
     */
    @Override
    @Cacheable(value = "store", key = "methodName + #categoryId")
    public List<Store> findByCategoryId(long categoryId) {
        return this.list(this.getQueryWrapper(Store::getCategoryId, categoryId));
    }

    /**
     * 根据categoryId查询商店（分页）
     *
     * @param categoryId 类别id
     * @return 商店列表
     */
    @Override
    @Cacheable(value = "store", key = "methodName + #categoryId + #query.toString()")
    public IPage<StoreVO> findByCategoryId(long categoryId, Query query) {
        IPage<StoreVO> page = PageUtils.getQuery(query);
        Wrapper<Store> wrapper = new QueryWrapper<Store>()
                .eq("category_id", categoryId);
        return this.baseMapper.selectByPage(page, wrapper);
    }

    /**
     * 根据id查询商店
     *
     * @param id 商店id
     * @return 商店列表
     */
    @Override
    @Cacheable(value = "store", key = "methodName + #id")
    public StoreVO detail(long id) {
        return this.baseMapper.detail(id);
    }

    /**
     * 排行榜
     *
     * @return 商店列表
     */
    @Override
    @Cacheable(value = "store", key = "methodName + #option.toString()")
    public List<Store> rank(Map<String, String> option) {
        Utils.checkQuantity(option);
        int quantity = Integer.parseInt(option.get("quantity"));
        Set<String> range = redisService.range("store_rank", 0, (quantity - 1));
        // 如果排行榜为空，将所有商店加入进去，分数为0
        if (range.size() == 0) {
            List<StoreVO> stores = getList();
            for (Store store : stores) {
                double score = Utils.changeRate(store.getRate(), store.getSales());
                redisService.incrScore("store_rank", String.valueOf(store.getId()), score);
            }
            range = redisService.range("store_rank", 0, (quantity - 1));
        }
        List<Store> stores = new ArrayList<>();
        for (String id : range) {
            stores.add(detail(Long.parseLong(id)));
        }
        return stores;
    }

    /**
     * 搜索
     *
     * @param keyword 关键词
     * @return 商店列表
     */
    @Override
    public SearchHits<Store> search(String keyword, Map<String, String> option) {
        return esPageHelper.build(QueryBuilders.matchQuery("name", keyword), option, Store.class);
    }

    /**
     * 是否存在商店
     *
     * @param id 商店id
     * @return boolean true存在，false不存在
     */
    @Override
    public boolean exist(long id) {
        return detail(id) != null;
    }

    /**
     * 根据名字查询商店
     *
     * @param name 名字
     * @return 查询结果
     */
    @Override
    @Cacheable(value = "store", key = "methodName + #name")
    public StoreVO findByName(String name) {
        return storeMapper.findByName(name);
    }
}