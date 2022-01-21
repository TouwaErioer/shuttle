package com.example.hope.service.serviceIpm;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.hope.base.service.imp.BaseServiceImp;
import com.example.hope.common.utils.JwtUtils;
import com.example.hope.common.utils.PageUtils;
import com.example.hope.common.utils.Utils;
import com.example.hope.config.exception.BusinessException;
import com.example.hope.config.redis.RedisService;
import com.example.hope.model.bo.Query;
import com.example.hope.model.entity.Orders;
import com.example.hope.model.entity.Product;
import com.example.hope.model.mapper.ProductMapper;
import com.example.hope.model.vo.ProductVO;
import com.example.hope.repository.elasticsearch.EsPageHelper;
import com.example.hope.repository.elasticsearch.ProductRepository;
import com.example.hope.service.business.OrderService;
import com.example.hope.service.business.ProductService;
import com.example.hope.service.business.StoreService;
import lombok.extern.log4j.Log4j2;

import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

@Log4j2
@Service
public class ProductServiceIpm extends BaseServiceImp<Product, ProductMapper> implements ProductService {

    @Resource
    private StoreService storeService;

    @Resource
    private RedisService redisService;

    @Resource
    private OrderService orderService;

    @Resource
    private ProductRepository productRepository;

    @Resource
    private EsPageHelper<Product> esPageHelper;

    /**
     * 添加产品
     *
     * @param product 产品
     */
    @Override
    @Transactional
    @CacheEvict(value = "product", allEntries = true)
    public boolean insert(Product product) {
        BusinessException.check(!storeService.exist(product.getStoreId()), "商店不存在");
        productRepository.save(product);
        return this.save(product);
    }

    /**
     * 增加产品销量
     *
     * @param id    产品id
     * @param sales 销量
     */
    @Override
    @Transactional
    public boolean addSales(long id, int sales) {
        Product product = getById(id, "产品不存在");
        product.setSales(product.getSales() + sales);
        redisService.review(product.getRate(), product.getSales(), "product_rank", String.valueOf(id));
        // 增加商店销量
        storeService.sales(findById(id).getStoreId(), sales);
        return this.updateById(product);
    }

    /**
     * 删除产品
     *
     * @param id 产品id
     */
    @Override
    @Transactional
    @CacheEvict(value = "product", allEntries = true)
    public boolean delete(long id) {
        BusinessException.check(orderService.deleteByPid(id), "删除订单失败");
        productRepository.deleteById(id);
        return this.removeById(id);
    }

    /**
     * 根据商店id删除产品
     *
     * @param storeId 商店id
     */
    @Override
    @Transactional
    @CacheEvict(value = "product", allEntries = true)
    public boolean deleteByStoreId(long storeId) {
        boolean state = false;
        for (Product product : findByStoreId(storeId)) {
            state = orderService.deleteByPid(product.getId());
        }
        return this.remove(this.getQueryWrapper(Product::getStoreId, storeId)) && state;
    }

    /**
     * 更新产品
     *
     * @param product 产品
     */
    @Override
    @Transactional
    @CacheEvict(value = "product", allEntries = true)
    public boolean update(Product product) {
        BusinessException.check(!storeService.exist(product.getStoreId()), "商店id不存在");
        productRepository.save(product);
        return this.updateById(product);
    }

    /**
     * 更新产品评分
     *
     * @param product 产品
     * @param token   Token
     */
    @Override
    @CacheEvict(value = "product", allEntries = true)
    public boolean review(Product product, String token, long orderId) {
        long userId = JwtUtils.getUserId(token);
        // 只允许下单此产品的用户或管理员对产品评分
        Orders orders = orderService.findById(orderId);
        boolean res;
        if ((orders.getStatus() == 0 && orders.getPid() == product.getId() && orders.getCid() == userId) || JwtUtils.is_admin(token)) {
            product = this.getById(product.getId(), "产品不存在");
            product.setRate(Utils.composeScore(product.getRate(), product.getSales()));
            res = this.updateById(product);
            redisService.review(product.getRate(), product.getSales(), "product_rank", String.valueOf(product.getId()));
        } else throw new BusinessException(0, "只允许下单此产品的用户对产品评分");
        return res;
    }

    /**
     * 查询全部产品
     *
     * @return 分页包装类
     */
    @Override
    @Cacheable(value = "product", key = "methodName + #query.toString()")
    public IPage<ProductVO> page(Query query) {
        IPage<ProductVO> page = PageUtils.getQuery(query);
        return this.baseMapper.selectByPage(page, new QueryWrapper<>());
    }

    /**
     * 查询全部产品
     *
     * @return 产品列表
     */
    @Cacheable(value = "product", key = "methodName")
    public List<Product> getList() {
        return this.list();
    }

    /**
     * 根据storeId查询产品
     *
     * @param storeId 商店id
     * @return 产品列表
     */
    @Override
    @Cacheable(value = "product", key = "methodName + #storeId")
    public List<Product> findByStoreId(long storeId) {
        return this.list(this.getQueryWrapper(Product::getStoreId, storeId));
    }

    /**
     * 根据storeId查询产品（分页）
     *
     * @param storeId 商店id
     * @return 产品列表
     */
    @Override
    @Cacheable(value = "product", key = "methodName + #storeId + #query.toString()")
    public IPage<ProductVO> findByStoreId(long storeId, Query query) {
        IPage<ProductVO> page = PageUtils.getQuery(query);
        Wrapper<Product> wrapper = new QueryWrapper<Product>()
                .eq("a.store_id", storeId);
        return this.baseMapper.selectByPage(page, wrapper);
    }

    /**
     * 根据id查询产品
     *
     * @param id 产品id
     * @return 产品
     */
    @Override
    @Cacheable(value = "product", key = "methodName + #id")
    public ProductVO findById(long id) {
        return this.baseMapper.detail(id);
    }

    /**
     * 排行榜
     *
     * @return 产品列表
     */
    @Override
    public List<ProductVO> rank(Map<String, String> option) {
        Utils.checkQuantity(option);
        int quantity = Integer.parseInt(option.get("quantity"));
        Set<String> rank = redisService.range("product_rank", 0, (quantity - 1));
        // 如果排行榜为空，将所有产品加入进去，分数为0
        if (rank.size() == 0) {
            List<Product> products = getList();
            for (Product product : products) {
                double score = Utils.changeRate(product.getRate(), product.getSales());
                redisService.incrScore("product_rank", String.valueOf(product.getId()), score);
            }
            rank = redisService.range("product_rank", 0, (quantity - 1));
        }
        List<ProductVO> products = new ArrayList<>();
        for (String id : rank) {
            products.add(findById(Long.parseLong(id)));
        }
        return products;
    }

    /**
     * 搜索
     *
     * @param keywords 关键词
     * @param option   分页参数
     * @return 分页包装类
     */
    @Override
    public SearchHits<Product> search(String keywords, Map<String, String> option) {
        return esPageHelper.build(QueryBuilders.matchQuery("name", keywords), option, Product.class);
    }

    /**
     * 是否存在产品
     *
     * @param id 产品id
     * @return boolean
     */
    @Override
    public boolean exist(long id) {
        return findById(id) != null;
    }
}