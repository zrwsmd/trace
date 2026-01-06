package com.yt.server;


import cn.ipokerface.mybatis.parser.ConfigurationParser;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.ggalmazor.ltdownsampling.LTThreeBuckets;
import com.google.common.collect.Lists;
import com.yt.server.entity.*;
import com.yt.server.mapper.TableNumInfoMapper;
import com.yt.server.mapper.TraceTableRelatedInfoMapper;
import com.yt.server.service.LagFullTableHandler;
import com.yt.server.util.BaseUtils;
import com.yt.server.util.ConnectionManager;
import com.yt.server.util.VarConst;
import javassist.CannotCompileException;
import javassist.NotFoundException;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.junit.jupiter.api.Test;
import org.mybatis.generator.api.MyBatisGenerator;
import org.mybatis.generator.config.Configuration;
import org.mybatis.generator.exception.InvalidConfigurationException;
import org.mybatis.generator.exception.XMLParserException;
import org.mybatis.generator.internal.DefaultShellCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.yt.server.service.IoComposeServiceDatabase.*;
import static com.yt.server.util.BaseUtils.*;

@SpringBootTest
class DemoApplicationTests {
    static List<UniPoint> singleVarDataList = new ArrayList<>();
    static {
        final Random random = new Random();
        String varName = "";
        int a=0;
        for (int i = 0; i < 1000; i++) {
            if (i % 8 == 0) {
                varName = "v0";
                a=-1;
            } else if (i % 127 == 0) {
                varName = "v1";
                a=0;
            } else {
                varName = "v2";
                a=random.nextInt(1);
            }
            UniPoint uniPoint = new UniPoint(new BigDecimal(i), new BigDecimal(a), varName);
            singleVarDataList.add(uniPoint);
        }
    }

//    @Autowired
//    private TraceFieldMetaMapper traceFieldMetaMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TableNumInfoMapper tableNumInfoMapper;

    //    @Autowired
//    private Trace1Mapper trace1Mapper;
//
    @Autowired
    private TraceTableRelatedInfoMapper traceTableRelatedInfoMapper;

    @Autowired
    private DataSource dataSource;

//    @Autowired
//    private TraceTimestampStatisticsMapper traceTimestampStatisticsMapper;

    @Autowired
    private AA aa;

    @Autowired
    private BB bb;

//    @Autowired
//    private DataSource dataSource;

//    @Autowired
//    private ShardingRuleConfiguration shardingRuleConfiguration;

    public static final String[] DEFAULT_TABLE = new String[]{"table_num_info", "trace_field_meta", "trace_table_related_info"};

    Set<String> backUpTableList = new TreeSet<>();

    {
        backUpTableList.addAll(Arrays.asList(DEFAULT_TABLE));
    }

    private static final Integer CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    private ThreadPoolExecutor pool = new ThreadPoolExecutor(CORE_POOL_SIZE, CORE_POOL_SIZE * 2, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(100));


    //    @Autowired
//    private TraceTableRelatedInfoMapper traceTableRelatedInfoMapper;

//	@Autowired
//	private TraceDownsamplingMapper traceDownsamplingMapper;

//	@Autowired
//	private TraceTableRelatedInfoMapper traceTableRelatedInfoMapper;

    @Test
    void test() throws NotFoundException, CannotCompileException, SQLException {
        final Connection connection = dataSource.getConnection();
        System.out.println(connection);
        //BaseUtils.addAnnotation("");
    }

    @Test
    void test1() {
        DataSourceInitializer dataSourceInitializer = new DataSourceInitializer();
//		TraceDownsamplingExample traceDownsampling=new TraceDownsamplingExample();
//		final TraceDownsamplingExample.Criteria criteria = traceDownsampling.createCriteria();
//		criteria.andTraceIdEqualTo(1L);
//		criteria.andVarNameEqualTo("earn");
//		final List<TraceDownsampling> traceDownsamplings = traceDownsamplingMapper.selectByExample(traceDownsampling);
//		for (TraceDownsampling downsampling : traceDownsamplings) {
//			System.out.println(downsampling.getVarName()+","+downsampling.getValue());
//		}
    }


    @Test
    void testGenerate() throws SQLException, IOException, InterruptedException, XMLParserException, InvalidConfigurationException {
        List<String> warnings = new ArrayList<String>();

        boolean overwrite = true;

        File configFile = new File("src/main/resources/generate/generatorConfig.xml");

        ConfigurationParser cp = new ConfigurationParser(warnings);

        Configuration config = cp.parseConfiguration(configFile);

        DefaultShellCallback callback = new DefaultShellCallback(overwrite);

        MyBatisGenerator myBatisGenerator = new MyBatisGenerator(config,

                callback, warnings);

        myBatisGenerator.generate(null);
    }

    @Test
    void testXml() throws Exception {
        File directory = new File("");
        String path = directory.getCanonicalPath();
        String filePath = path+"/src/main/resources/generate/generatorConfig.xml";
        // 1、创建 File 对象，映射 XML 文件
//		File file = new File(filePath);
//		// 2、创建 DocumentBuilderFactory 对象，用来创建 DocumentBuilder 对象
//		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
//		// 3、创建 DocumentBuilder 对象，用来将 XML 文件 转化为 Document 对象
//		DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
//		// 4、创建 Document 对象，解析 XML 文件
//		Document document = documentBuilder.parse(file);
        SAXReader reader = new SAXReader();
        Document document = reader.read(new File(filePath));
        // 修改第一个 student
        // 5、获取第一个 student 结点
        //获得某个节点的属性对象
        Element rootElem = document.getRootElement();
        //首先要知道自己要操作的节点。
        List<Element> pList = rootElem.elements("context");
        // 写入文件
        XMLWriter xmlWriter = null;
        FileOutputStream fos = null;
        final Element e = pList.get(0);
        List<Element> contactList = e.elements("table");
        final Element element = contactList.get(0);
        Attribute tableNameAttr = element.attribute("tableName");
        Attribute domainObjectNameAttr = element.attribute("domainObjectName");
        element.remove(tableNameAttr);
        element.remove(domainObjectNameAttr);
        final Element newElement = element.addAttribute("tableName", "ee");
        element.addAttribute("domainObjectName", "ff");
        fos = new FileOutputStream(new File("src/main/resources/generate/generatorConfig.xml"));
        // 设置输出格式
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setEncoding("utf-8");
        format.setLineSeparator("\n");

        // 创建writer(参数一：输出流，参数二L：输出格式)
        xmlWriter = new XMLWriter(fos, format);
        // 写数据
        xmlWriter.write(document);

//		for (Element itme : pList) {
//			List<Element> contactList = itme.elements("table");
//			Element element = contactList.get(0);
//			MybatisGeneratorTable routeModel = new MybatisGeneratorTable();
//			Attribute tableNameAttr = element.attribute("tableName");
//			Attribute domainObjectNameAttr = element.attribute("domainObjectName");
////			System.out.println(tableNameAttr.getValue());
////			System.out.println(domainObjectNameAttr.getValue());
//			element.remove(domainObjectNameAttr);
//			final Element newElement = element.addAttribute("domainObjectName", "bbbb");
//			final Attribute domainObjectName = newElement.attribute("domainObjectName");
//			System.out.println(domainObjectName.getValue());
//			fos = new FileOutputStream(new File("generatorConfig.xml"));
//
//			// 设置输出格式
//			OutputFormat format = OutputFormat.createPrettyPrint();
//			format.setEncoding("utf-8");
//			format.setLineSeparator("\n");
//
//			// 创建writer(参数一：输出流，参数二L：输出格式)
//			xmlWriter = new XMLWriter(fos, format);
//			// 写数据
//			xmlWriter.write(document);
//
//		//	tableNameAttr.a
//
//		}

//		Node student = document.getElementsByTagName("table").item(0);
//		System.out.println(student);

    }

    @Test
    void testJdbcInsert() throws Exception {
        TraceTableRelatedInfo traceTableRelatedInfo = new TraceTableRelatedInfo();
        traceTableRelatedInfo.setTraceId(BaseUtils.generateTraceId());
        traceTableRelatedInfo.setTableName("trace1");
        String sql = "INSERT INTO test.trace_table_related_info (traceId, tableName) VALUES(?,?)";
        Object[] params = new Object[2];
        params[0] = BaseUtils.generateTraceId();
        params[1] = "trace1";
        jdbcTemplate.update(sql, params);
    }


    @Test
    void testJdbcCount() {
        Object[] regionParam = new Object[]{1, 1000000};
        String sql = "select count(*) from trace1 where id between ? and ?  ";
        long start = System.currentTimeMillis();
        final Integer count = jdbcTemplate.queryForObject(sql, regionParam, Integer.class);
        long end = System.currentTimeMillis();
        System.out.println("花费了" + (end - start) + "ms");//21ms
        System.out.println("count=" + count);

    }


//    @Test
//    void testJdbcMultiValueMap() throws Exception {
//        MultiValueMap allMultiValueMap = new MultiValueMap();
//        String sql = "SELECT * FROM trace_downsampling where traceId= " + "" + " and varName= " + "earn" + " and downSamplingRate= " + 4;
//        List<UniPoint> singleVarDataList = new ArrayList<>();
//        final Random random = new Random();
//
//        for (int j = 0; j < 5; j++) {
//            UniPoint uniPoint = new UniPoint(new BigDecimal(j), new BigDecimal(random.nextInt(100000)));
//            singleVarDataList.add(uniPoint);
//        }
//        List<TraceDownsampling> traceDownsamplingList = convert2DownSamplingPojo(singleVarDataList, 666L, "earn", 4);
//        MultiValueMap multiValueMap = convert2MultiMapForTraceDownSampling(traceDownsamplingList);
//        allMultiValueMap.putAll(multiValueMap);
//        singleVarDataList.clear();
//
//        for (int j = 0; j < 5; j++) {
//            UniPoint uniPoint = new UniPoint(new BigDecimal(j), new BigDecimal(random.nextInt(100000)));
//            singleVarDataList.add(uniPoint);
//        }
//        List<TraceDownsampling> traceDownsamplingList2 = convert2DownSamplingPojo(singleVarDataList, 666L, "pwd", 4);
//        MultiValueMap multiValueMap2 = convert2MultiMapForTraceDownSampling(traceDownsamplingList2);
//        allMultiValueMap.putAll(multiValueMap2);
//        singleVarDataList.clear();
//
//        for (int j = 0; j < 5; j++) {
//            UniPoint uniPoint = new UniPoint(new BigDecimal(j), new BigDecimal(random.nextInt(100000)));
//            singleVarDataList.add(uniPoint);
//        }
//        List<TraceDownsampling> traceDownsamplingList3 = convert2DownSamplingPojo(singleVarDataList, 666L, "age", 4);
//        MultiValueMap multiValueMap3 = convert2MultiMapForTraceDownSampling(traceDownsamplingList3);
//        allMultiValueMap.putAll(multiValueMap3);
//        singleVarDataList.clear();
//
//        for (int j = 5; j < 10; j++) {
//            UniPoint uniPoint = new UniPoint(new BigDecimal(j), new BigDecimal(random.nextInt(6666)));
//            singleVarDataList.add(uniPoint);
//        }
//        List<TraceDownsampling> traceDownsamplingList4 = convert2DownSamplingPojo(singleVarDataList, 666L, "age", 4);
//        MultiValueMap multiValueMap4 = convert2MultiMapForTraceDownSampling(traceDownsamplingList4);
//        allMultiValueMap.putAll(multiValueMap4);
//        singleVarDataList.clear();
//
//        System.out.println(allMultiValueMap);
//    }


    @Test
    void testUnipoint2Obj() throws Exception {
        System.out.println(singleVarDataList);
        List<Object[]> objects = new ArrayList<>();
        for (int i = 1000000; i < 4000000; i++) {
            Object[] objArr = new Object[4];
            objArr[0] = i;
            objArr[1] = i + 2;
            objArr[2] = 18;
            objArr[3] = i + 5;
            objects.add(objArr);
        }
        String insertSql = "INSERT INTO trace1 (id, earn, age, pwd) VALUES(?,?,?,?)";
        jdbcTemplate.batchUpdate(insertSql, objects);
    }

    @Test
    void testJdbcBetween() throws Exception {

        //1000000 10000000
        Object[] regionParam = new Object[]{1000000, 1900000};

        long start = System.currentTimeMillis();
        String originalRegionSql = " select id,v0,v1,v2,v3 from trace2_1 where id between ? and ? ";
        //  String originalRegionSql = " select id,earn,age,pwd from trace1 where id>5000000 limit 9000000  ";
        // String originalRegionSql = " select * from test.trace1   where id>=  (select id from test.trace1  limit 1000000, 1)";
        // select * from test.trace1   where id>=
        // (select id from test.trace1  limit 100000, 1)limit 800000
        // String originalRegionSql = "select * from trace1  where id >=(select id from trace1 limit ? ,1)limit ? ";
        final List<Map<String, Object>> list = jdbcTemplate.queryForList(originalRegionSql, regionParam);
        long end = System.currentTimeMillis();
        System.out.println(list.size());
        System.out.println("花费了" + (end - start) + "ms");//21ms
    }

    @Test
    void testDelete() throws IOException, ClassNotFoundException {
        String sql = "delete from trace1";
        jdbcTemplate.execute(sql);
    }


    @Test
    void testBatchInsert() throws IOException, ClassNotFoundException, SQLException {
        Connection connection = ConnectionManager.getConnection("test");
        //insertDownsampling(connection);
        // insertDownsampling(connection);
//        List<Object[]> objects = new ArrayList<>();
//            for (int i = 1000000; i < 4000000; i++) {
//                Object[] objArr = new Object[4];
//                objArr[0] = i;
//                objArr[1] = i +2;
//                objArr[2] = 18;
//                objArr[3] = i +5;
//                objects.add(objArr);
//            }
//            String insertSql = "INSERT INTO trace1 (id, earn, age, pwd) VALUES(?,?,?,?)";
//            jdbcTemplate.batchUpdate(insertSql, objects);

    }

//    @Test
//    public static void insertDownsampling(Connection conn) {
//        // 开始时间
//        Long begin = System.currentTimeMillis();
//        // sql前缀
//        //String prefix = "INSERT INTO trace1 (id, earn, age, pwd) VALUES";
//        String prefix = " INSERT INTO test.trace_downsampling (id, varName, `timestamp`, value, downSamplingRate) VALUES";
//        try {
//            // 保存sql后缀
//            StringBuffer suffix = new StringBuffer();
//            // 设置事务为非自动提交
//            conn.setAutoCommit(false);
//            // 比起st，pst会更好些
//            PreparedStatement pst = conn.prepareStatement(" ");//准备执行语句
//            final Random random = new Random(100);
//            AtomicInteger ai = new AtomicInteger();
//            // 外层循环，总提交事务次数
//            for (int i = 1; i <= 100; i++) {
//                suffix = new StringBuffer();
//                // 第j次提交步长
//                for (int j = 1; j <= 10000; j++) {
//                    // 构建SQL后缀
//                    suffix.append("(");
//                    suffix.append(333333333 + ",");
//                    suffix.append(j + ",");
//                    suffix.append(i + ",");
//                    suffix.append(j + 5 + ",");
//                    suffix.append(8 + "");
//                    suffix.append("),");
//                }
//                // 构建完整SQL
//                String sql = prefix + suffix.substring(0, suffix.length() - 1);
//                // 添加执行SQL
//                pst.addBatch(sql);
//                // 执行操作
//                pst.executeBatch();
//                // 提交事务  使用insert批量插入，每次插入10万条数据就提交一次事务，节省了大量时间
//                conn.commit();
//                // 清空上一次添加的数据
//                suffix = new StringBuffer();
//            }
//            // 头等连接
//            pst.close();
//            conn.close();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//        // 结束时间
//        Long end = System.currentTimeMillis();
//        // 耗时
//        System.out.println("1000万条数据插入花费时间 : " + (end - begin) / 1000 + " s");//107s
//        System.out.println("插入完成");
//    }
//
//    @Test
//    public static void insert(Connection conn) {
//        // 开始时间
//        Long begin = System.currentTimeMillis();
//        // sql前缀
//        String prefix = "INSERT INTO trace1 (id, earn, age, pwd) VALUES";
//        try {
//            // 保存sql后缀
//            StringBuffer suffix = new StringBuffer();
//            // 设置事务为非自动提交
//            conn.setAutoCommit(false);
//            // 比起st，pst会更好些
//            PreparedStatement pst = conn.prepareStatement(" ");//准备执行语句
//            AtomicInteger ai = new AtomicInteger();
//            // 外层循环，总提交事务次数

    /// /            for (int i = 1000000; i <= 2000000; i++) {
    /// /               String sql = "INSERT INTO trace1_1 VALUES (" + i + ",999999, 20, 66666666)";
    /// /                pst.addBatch(sql);
    /// /            }
//            for (int i = 1; i <= 100; i++) {
//                suffix = new StringBuffer();
//                // 第j次提交步长
//                for (int j = 1; j <= 100000; j++) {
//                    // 构建SQL后缀
//                    suffix.append("(");
//                    suffix.append(ai.incrementAndGet() + ",");
//                    suffix.append(j + ",");
//                    suffix.append(i + ",");
//                    suffix.append(j + 5 + "");
//                    suffix.append("),");
//                }
//                // 构建完整SQL
//                String sql = prefix + suffix.substring(0, suffix.length() - 1);
//                // 添加执行SQL
//                pst.addBatch(sql);
//                // 执行操作
//                pst.executeBatch();
//                // 提交事务  使用insert批量插入，每次插入10万条数据就提交一次事务，节省了大量时间
//                conn.commit();
//                // 清空上一次添加的数据
//                suffix = new StringBuffer();
//            }
//            // 头等连接
//            pst.close();
//            conn.close();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//        // 结束时间
//        Long end = System.currentTimeMillis();
//        // 耗时
//        System.out.println("1000万条数据插入花费时间 : " + (end - begin) / 1000 + " s");//107s
//        System.out.println("插入完成");
//    }

    @Test

    public void Test() {

        Connection conn = null;

        PreparedStatement pstm = null;

        ResultSet rt = null;

        try {

            Class.forName("com.mysql.cj.jdbc.Driver");

            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/test?useUnicode=true&characterEncoding=utf-8&useSSL=false", "root", "123456");

            String sql = "INSERT INTO trace1_6(id, earn, age, pwd) VALUES (?,?,?,?)";

            pstm = conn.prepareStatement(sql);

            conn.setAutoCommit(false);

            Long startTime = System.currentTimeMillis();

            Random rand = new Random();

            int a, b, c, d;

            for (int i = 600000; i < 1600000; i++) {

                pstm.setInt(1, i);

                pstm.setLong(2, 6666666);

                pstm.setInt(3, 24);

                pstm.setLong(4, 8888);

                pstm.addBatch();

            }

            pstm.executeBatch();

            conn.commit();

            Long endTime = System.currentTimeMillis();

            System.out.println("OK,用时：" + (endTime - startTime));

        } catch (Exception e) {

            e.printStackTrace();

            throw new RuntimeException(e);

        } finally {

            if (pstm != null) {

                try {

                    pstm.close();

                } catch (SQLException e) {

                    e.printStackTrace();

                    throw new RuntimeException(e);

                }

            }

            if (conn != null) {

                try {

                    conn.close();

                } catch (SQLException e) {

                    e.printStackTrace();

                    throw new RuntimeException(e);

                }

            }

        }

    }

    @Test
    public void insert2(Connection conn) throws SQLException {
        // 设置事务为非自动提交
        conn.setAutoCommit(false);
        // 比起st，pst会更好些
        PreparedStatement pst = conn.prepareStatement(" ");//准备执行语句
        final Random random = new Random(100);
        AtomicInteger ai = new AtomicInteger();
        // 外层循环，总提交事务次数
        for (int i = 0; i <= 1000000; i++) {
            String sql = "INSERT INTO trace1 VALUES (" + i + ",999999, 20, 66666666)";
            jdbcTemplate.execute(sql);
        }
    }

    @Test
    void testInsertBatch() throws IOException {
//        List<TraceFieldMeta>traceFieldMetaList=new ArrayList<>();
//        for (int i = 0; i < 5; i++) {
//            TraceFieldMeta traceFieldMeta=new TraceFieldMeta();
//            traceFieldMeta.setVarName("v"+i);
//            traceFieldMeta.setTraceId(666L);
//            traceFieldMeta.setWindowsType("dword");
//            traceFieldMetaList.add(traceFieldMeta);
//        }
//        traceFieldMetaMapper.insertBatch(traceFieldMetaList);
//        StringBuilder samplingSql = new StringBuilder();
//        samplingSql.append(" CREATE TABLE " + "`").append("traceyt_sampling555").append("`").append("(");
//        samplingSql.append("`id` bigint NOT NULL AUTO_INCREMENT, ");
//        samplingSql.append(" `varName` varchar(50) DEFAULT NULL, ");
//        samplingSql.append(" `timestamp` bigint DEFAULT NULL, ");
//        samplingSql.append(" `value` decimal(30,8) DEFAULT NULL, ");
//        samplingSql.append(" `downSamplingRate` int DEFAULT NULL, ");
//        samplingSql.append(" PRIMARY KEY (`id`) ");
//        samplingSql.append(" ) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;");
//        String samplingSqlStr = samplingSql.toString();
//        jdbcTemplate.execute(samplingSqlStr);


        String sql = "drop table  " + "trace1";
        jdbcTemplate.update(sql);
    }



    @Test
    public void insertTest() {
//      Trace1 trace1=new Trace1();
//        trace1.setId(999L);
//      trace1.setAge((byte) 29);
//      trace1.setEarn(8888);
//      trace1.setPwd(123456);
//      trace1Mapper.insert(trace1);

        Object[] param = new Object[]{3009400L, 777, 18, 999};
        String sql = "INSERT INTO trace1 (id,earn, age, pwd) VALUES(?,?,?,?)";
//
        jdbcTemplate.update(sql, param);


//        String countSql="select num from table_num_info";
//        final List<TableNumInfo> numInfoList = jdbcTemplate.query(countSql, new BeanPropertyRowMapper<>(TableNumInfo.class));
//        System.out.println(numInfoList.get(0).getNum());
    }


    @Test
    void testReflectBatchInsert() throws IOException, ClassNotFoundException {
//        String downsamplingTableName="trace_downsampling";
//        List<Object[]> dataObjArr = convertPojoList2ObjListArr(list, 4,16);
//        String traceDownsamplingBatchSql = "INSERT INTO "+downsamplingTableName+ " (varName, `timestamp`, value, downSamplingRate) VALUES(?,?,?,?)";
//        jdbcTemplate.batchUpdate(traceDownsamplingBatchSql, dataObjArr);
        Set<UniPoint> dataSet = new LinkedHashSet<>();
        UniPoint uniPoint0 = new UniPoint(new BigDecimal(1), new BigDecimal(55), "var0");
        UniPoint uniPoint2 = new UniPoint(new BigDecimal(1), new BigDecimal(66), "var1");
        UniPoint uniPoint3 = new UniPoint(new BigDecimal(1), new BigDecimal(77), "var2");
        UniPoint uniPoint4 = new UniPoint(new BigDecimal(2), new BigDecimal(155), "var0");
        UniPoint uniPoint5 = new UniPoint(new BigDecimal(2), new BigDecimal(255), "var1");
        UniPoint uniPoint6 = new UniPoint(new BigDecimal(2), new BigDecimal(355), "var2");
        UniPoint uniPoint7 = new UniPoint(new BigDecimal(3), new BigDecimal(455), "var0");
        UniPoint uniPoint8 = new UniPoint(new BigDecimal(3), new BigDecimal(555), "var1");
        UniPoint uniPoint9 = new UniPoint(new BigDecimal(3), new BigDecimal(655), "var2");
        UniPoint uniPoint10 = new UniPoint(new BigDecimal(4), new BigDecimal(755), "var0");
        UniPoint uniPoint11 = new UniPoint(new BigDecimal(4), new BigDecimal(855), "var1");
        UniPoint uniPoint12 = new UniPoint(new BigDecimal(4), new BigDecimal(955), "var2");
        dataSet.add(uniPoint0);
        dataSet.add(uniPoint4);
        dataSet.add(uniPoint2);
        dataSet.add(uniPoint3);
        dataSet.add(uniPoint5);
        dataSet.add(uniPoint6);
        dataSet.add(uniPoint7);
        dataSet.add(uniPoint8);
        dataSet.add(uniPoint9);
        dataSet.add(uniPoint10);
        dataSet.add(uniPoint11);
        dataSet.add(uniPoint12);
        final Map<BigDecimal, List<UniPoint>> dataMap = dataSet.stream()
                .collect(Collectors.groupingBy(UniPoint::getX));
        final Collection<List<UniPoint>> values = dataMap.values();
        final Object[] objects = values.toArray();
        System.out.println(objects);
        List<Object[]> allList = new ArrayList<>();
        for (Object object : objects) {
            List<UniPoint> list = (List<UniPoint>) object;
            final UniPoint uniPoint = list.get(0);
            BigDecimal x = uniPoint.getX();
            Object[] singleObj = new Object[list.size() + 1];
            for (int i = 0; i < list.size(); i++) {
                if (i == 0) {
                    singleObj[0] = x;
                }
                singleObj[i + 1] = list.get(i).getY();
                if (i == list.size() - 1) {
                    allList.add(singleObj);
                }

            }

        }


        allList.sort((o1, o2) -> (((BigDecimal) o1[0]).intValue() - ((BigDecimal) o2[0]).intValue()));
        final Object[] firstObj = allList.get(0);
        final Object firstObjTimestamp = firstObj[0];
        final Object[] lastObj = allList.get(allList.size() - 1);
        final Object lastObjTimestamp = lastObj[0];
        Long firstTimestamp = ((BigDecimal) firstObjTimestamp).longValue();
        Long lastTimestamp = ((BigDecimal) lastObjTimestamp).longValue();
        System.out.println(lastTimestamp);
    }

    private int chooseBucket(Long timestamp) {
        for (int i = 0; i < 10; i++) {
            final long first = (long) i * 10000000 / 20;
            //eg:if (timestamp>0 && timestamp<=1000000)  if (timestamp>1000000 && timestamp<=2000000)
            if (timestamp > first && timestamp <= first + 10000000 / 20) {
                return i;
            }
        }
        return -1;

    }

    @Test
    void testTableNumInfo() throws IOException, ClassNotFoundException {

        System.out.println(2 << 13);
//        final int i = chooseBucket(3230000L);
//     //   final TableNumInfo tableNumInfo = tableNumInfoMapper.selectByPrimaryKey(1);
//        System.out.println(i);
//        for (int i = 0; i < 3; i++) {
//            StringBuilder sql = new StringBuilder();
//            sql.append("DROP TABLE IF EXISTS " + "`").append("eee".concat("_").concat(String.valueOf(i))).append("`").append(";");
//            sql.append(" CREATE TABLE " + "`").append("eee".concat("_").concat(String.valueOf(i))).append("`").append("(");
//            sql.append("`id` bigint NOT NULL,");
//            String sqlStr = sql.toString();
//            jdbcTemplate.execute(sqlStr);
//        }
    }

//    @Test
//    void testFork() throws ExecutionException, InterruptedException {
//        Long reqStartTimestamp = 1200000L;
//        Long reqEndTimestamp = 2500000L;
//        MultiValueMap allMultiValueMap = new MultiValueMap();
//        List<Future<MultiValueMap>> resultList = new ArrayList<>();
//        final long start = System.currentTimeMillis();
//        final List<String> queryTableList = getQueryTable(reqStartTimestamp, reqEndTimestamp, "trace1");
//        ForkJoinPool pool = new ForkJoinPool();
//        for (String s : queryTableList) {
//            final QueryTableHandlerForkJoin tableHandlerForkJoin = new QueryTableHandlerForkJoin(s, reqStartTimestamp, reqEndTimestamp, "trace1", jdbcTemplate);
//            final MultiValueMap multiValueMap = pool.invoke(tableHandlerForkJoin);
//            allMultiValueMap.putAll(multiValueMap);
//        }
//        final long end = System.currentTimeMillis();
//        System.out.println("fork join花费了" + (end - start) + "ms");
//    }
//
//    @Test
//    void testThread() throws ExecutionException, InterruptedException {
//        try {
//            Long reqStartTimestamp = 0L;
//            Long reqEndTimestamp = 1000000L;
//            MultiValueMap allMultiValueMap = new MultiValueMap();
//            List<Future<MultiValueMap>> resultList = new ArrayList<>();
//            final long start = System.currentTimeMillis();
//            final List<String> queryTableList = getQueryTable(reqStartTimestamp, reqEndTimestamp, "trace3");
//            CountDownLatch countDownLatch = new CountDownLatch(queryTableList.size());
//            for (String s : queryTableList) {
//                String fieldName = "v0,v1,v2,v3,v4,v5,v6,v7,v8,v9";
//                Future<MultiValueMap> future = pool.submit(new QueryFullTableHandler(s, reqStartTimestamp, reqEndTimestamp,  jdbcTemplate, countDownLatch, fieldName, null));
//                resultList.add(future);
//            }
//            countDownLatch.await();
//            for (Future<MultiValueMap> future : resultList) {
//                allMultiValueMap.putAll(future.get());
//            }
//            final long end = System.currentTimeMillis();
//            System.out.println("多线程花费了" + (end - start) + "ms");
//        } finally {
//            pool.shutdown();
//        }
//
//    }

    @Test
    public void testOriginal2() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        Object[] regionParam = new Object[]{60L, 1090000L};
        String originalRegionSql = "select * from " + "trace3_0" + " where id between ? and ? ";
        long start = System.currentTimeMillis();
        List<Map<String, Object>> list = jdbcTemplate.queryForList(originalRegionSql, regionParam);
//        MultiValueMap multiValueMap = convert2MultiMap(list);
//        System.out.println(list);
        long end = System.currentTimeMillis();
        System.out.println("花费了" + (end - start) + "ms");//21ms
    }

//    public static List<String> getQueryTable(Long reqStartTimestamp, Long reqEndTimestamp, String parentTable) {
//        List<String> list = new ArrayList<>();
//        if (reqStartTimestamp > reqEndTimestamp) {
//            throw new RuntimeException("开始时间戳不能大于结束时间戳!");
//        }
//        if (reqStartTimestamp > 9000000) {
//            list.add(parentTable.concat("_").concat(String.valueOf(9)));
//        }
//        if (reqEndTimestamp < 1000000) {
//            list.add(parentTable.concat("_").concat(String.valueOf(0)));
//        }
//        int beginSlot = (int) (reqStartTimestamp / 1000000);
//        int endSlot = (int) (reqEndTimestamp / 1000000);
//        for (int i = beginSlot; i <= endSlot; i++) {
//            list.add(parentTable.concat("_").concat(String.valueOf(i)));
//        }
//        return list;
//    }

//    @Test
//    void testThread2() throws ExecutionException, InterruptedException {
//        try {
//            Long reqStartTimestamp = 0L;
//            Long reqEndTimestamp = 9999999L;
//            MultiValueMap allMultiValueMap = new MultiValueMap();
//            List<Future<MultiValueMap>> resultList = new ArrayList<>();
//            final long start = System.currentTimeMillis();
//            final List<String> queryTableList = getQueryTable(reqStartTimestamp, reqEndTimestamp, "trace1");
//            CountDownLatch countDownLatch = new CountDownLatch(queryTableList.size());
//            for (String s : queryTableList) {
//                Future<MultiValueMap> future = pool.submit(new QueryFullTableHandler(s, reqStartTimestamp, reqEndTimestamp,  jdbcTemplate, countDownLatch, "*", null));
//                resultList.add(future);
//            }
//            countDownLatch.await();
//            for (Future<MultiValueMap> future : resultList) {
//                allMultiValueMap.putAll(future.get());
//            }
////            final Object earn = allMultiValueMap.get("age");
////            List<BigDecimal[]>list= (List<BigDecimal[]>) earn;
////            System.out.println(list.get(list.size()-1));
//            final long end = System.currentTimeMillis();
//            System.out.println("多线程花费了" + (end - start) + "ms");
//        } finally {
//            pool.shutdown();
//        }
//
//    }


    @Transactional(rollbackFor = Exception.class)
    public void rollbackShardingTable(String tableName) {
        Consumer<String> consumer = (s) -> {
            for (int a = 0; a < 10; a++) {
                String sql = "drop table " + tableName.concat("_").concat(String.valueOf(a));
                jdbcTemplate.update(sql);
            }
        };
        consumer.accept(tableName);
    }

    public Consumer<String> rollbackShardingTable2() {
        Consumer<String> consumer = (s) -> {
            for (int a = 0; a < 10; a++) {
                String sql = "drop table " + s.concat("_").concat(String.valueOf(a));
                jdbcTemplate.update(sql);
            }
        };
        return consumer;
    }

    @Test
    void testConsumer() throws ExecutionException, InterruptedException {
        rollbackShardingTable2().accept("trace2");

    }

    @Test
    void testUpdate() {
//        TraceTableRelatedInfo traceTableRelatedInfo=new TraceTableRelatedInfo();
//        traceTableRelatedInfo.setTraceId(266L);
//        traceTableRelatedInfo.setTraceName("33");
//        traceTableRelatedInfo.setTraceConfig("{:555}");
//        traceTableRelatedInfo.setDownsamplingTableName("777");
//        traceTableRelatedInfo.setTraceConfig("123");
//        traceTableRelatedInfo.setReachedBatchFlag("1");
//        traceTableRelatedInfoMapper.insert(traceTableRelatedInfo);
        TraceTableRelatedInfo traceTableRelatedInfo = traceTableRelatedInfoMapper.selectByPrimaryKey(266L);
        traceTableRelatedInfo.setTraceConfig("777");
        traceTableRelatedInfo.setReachedBatchFlag("true");
        traceTableRelatedInfo.setTraceStatus("traceStart");
        traceTableRelatedInfoMapper.updateByPrimaryKey(traceTableRelatedInfo);
    }

    @Test
    void testJson() {
        String aa= """
                {"config":{"task":"","taskEnum":[{"name":"aa","cycle":11},{"name":"bb","cycle":22},{"name":"cc","cycle":33}],"uri":"xxxxx","isHis":false,"triggerEnable":false,"triggerVariable":"","triggerLevel":20,"triggerCnt":200,"triggerMode":"","triggerModeEnum":["上升沿","下降沿","上升沿+下降沿"],"sampleInterval":4,"timeUnit":"ms","sampleCnt":100,"recMode":"","cntMax":10000},"dataChannel":[{"name":"xxx","color":"","enableUpperLimit":false,"upperLimit":3999,"upperLimitColor":"","enableLowerLimit":false,"lowerLimit":0,"lowerLimitColor":""}]}
                """;
        String jsonStr = """
                tData:{
                      id:678949352707,
                          traceCfg:{
                          config:{
                              task:'',//选择任务，以此决定监控变量的采样周期。
                              taskEnum:[{name:'aa',cycle:11},{name:'bb',cycle:22},{name:'cc',cycle:33}],
                              uri:'xxxxx', //webview与java后台实例通信的uri
                              isHis:false,//true表示该配置载入自历史记录文件，如果有历史数据要绘制变量曲线。

                              //触发器配置
                              triggerEnable:false,
                              triggerVariable:'',
                              triggerLevel:20,//定义边沿大小
                              triggerCnt:200,//触发后采样多少个点
                              triggerMode:'',
                              triggerModeEnum: ['上升沿', '下降沿', '上升沿+下降沿'],

                              sampleInterval:6,//每隔几个周期采样一次
                              timeUnit:'ms',
                              sampleCnt:80,//配置采样点数
                              recMode:'',//记录模式,当超过最大采样点数后采用的模式
                              // recModeEnum:[  //这个配置不要了，相应的界面元素也不要了。默认就是循环采样。
                              //     {name:'noCycle',desc:'超过最大采样点时，停止采样'},
                              //     {name:'cycle',desc:'超过最大采样点时，循环采样'}
                              // ],
                              cntMax:10000,//最大的采样点数
                          },
                          dataChannel:[
                              {
                                  name:'xxx',
                                  color:'',
                                  enableUpperLimit:false,
                                  upperLimit:3999,
                                  upperLimitColor:'',
                                  enableLowerLimit:false,
                                  lowerLimit:0,
                                  lowerLimitColor:''
                              }
                          ],

                      },
                      vars:[
                          {
                              name:'earn',
                              type:'lword'
                          },
                          {
                            name:'age',
                            type:'sint'
                        },
                        {
                          name:'pwd',
                          type:'lword'
                      }
                      ]
                  }
                """;
        aa = aa.replace("\"triggerModeEnum\":[\"上升沿\",\"下降沿\",\"上升沿+下降沿\"],","");
        System.out.println(aa);


    }

    @Test
    void test2() throws ClassNotFoundException, SQLException {
        List<Object[]> dataObjArr = new ArrayList<>();
        final long start = System.currentTimeMillis();
        for (int i = 0; i < 1000053; i++) {
            Object[] obj = new Object[4];
            obj[0] = i;
            obj[1] = i;
            obj[2] = i * 2;
            obj[3] = 2;
            dataObjArr.add(obj);
        }
        Connection connection = ConnectionManager.getConnection("trace");
        BaseUtils.executeDownsamplingBatchUpdate(connection, "trace88888888_downsampling_v0_1024", dataObjArr);
        final long end = System.currentTimeMillis();
        System.out.println("花费了" + (end - start));
    }

    @Test
    void test33() throws ClassNotFoundException, SQLException {
        List<Object[]> dataObjArr = new ArrayList<>();
        final long start = System.currentTimeMillis();
        String traceBatchSql = "INSERT INTO trace3_9 (id, v0, v1, v2, v3, v4, v5, v6, v7, v8, v9) VALUES";
        for (int i = 9000000; i <=10000000; i++) {
            Object[] obj = new Object[11];
            obj[0] = i;
            obj[1] = 4000000;
            obj[2] = 24;
            obj[3] = 623456;
            obj[4] = 88888;
            obj[5] = 600;
            obj[6] = 7999;
            obj[7] = 99;
            obj[8] = 40000;
            obj[9] = 600000;
            obj[10] = 899700;
            dataObjArr.add(obj);
        }
        Connection connection = ConnectionManager.getConnection("trace");
        List<String> questionMarkList = new ArrayList<>();
        //加1是因为还有id列(固定列)
        for (int i = 0; i < 11; i++) {
            questionMarkList.add("?");
        }
        BaseUtils.executeFullTableBatchUpdate(connection, traceBatchSql, questionMarkList, dataObjArr);
        final long end = System.currentTimeMillis();
        System.out.println("花费了" + (end - start));
    }

    @Test
    public void testOriginalDownsampling() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        final Class<?> clazz = Class.forName(DOMAIN_PREFIX.concat("TraceDownsampling"));
        Object[] regionParam = new Object[]{2, 0, 1000000};
//        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
//        MapSqlParameterSource parameters = new MapSqlParameterSource();
//        parameters.addValue("ids", new String[]{"v0","v2"});
        String param = "'v0','v1'";
        String originalRegionSql = "select varName, timestamp, value from trace_downsampling where  downSamplingRate=? and timestamp between ? and ? and varName in(" + param + ")";

        //    String originalRegionSql = "select varName, timestamp, value from trace_downsampling where  downSamplingRate=? and timestamp between ? and ? and varName =? ";
        long start = System.currentTimeMillis();
        List list = jdbcTemplate.query(originalRegionSql, regionParam, new BeanPropertyRowMapper<>(clazz));
//        System.out.println(list);
        long end = System.currentTimeMillis();
        System.out.println("花费了" + (end - start) + "ms");//21ms
    }

    @Test
    public void testOriginalDownsampling2() {
        List<String> list = new ArrayList<>();
        list.add("v0");
        list.add("v2");
        Iterator<String> iterator = list.iterator();
        List<String> filterList = new ArrayList<>();
        while (iterator.hasNext()) {
            String element = iterator.next();
            String newElement = "'".concat(element).concat("'");
            iterator.remove();
            filterList.add(newElement);
        }

        System.out.println(StringUtils.join(filterList, ","));
    }

//    @Test
//    void testDownThread() throws ExecutionException, InterruptedException {
//        try {
//            Long reqStartTimestamp = 0L;
//            Long reqEndTimestamp = 9999999L;
//            Integer closestRate = 512;
//            MultiValueMap allMultiValueMap = new MultiValueMap();
//            List<Future<MultiValueMap>> resultList = new ArrayList<>();
//            final long start = System.currentTimeMillis();
//            final List<String> varList = Arrays.asList("v0", "v1","v2","v3","v4","v5","v6","v7","v8","v9");
//            CountDownLatch countDownLatch = new CountDownLatch(varList.size());
//            for (String varName : varList) {
//                Future<MultiValueMap> future = pool.submit(new QueryEachDownsamplingTableHandler("trace2_downsampling".concat("_").concat(varName), reqStartTimestamp, reqEndTimestamp, jdbcTemplate, countDownLatch, varName, closestRate,10, null));
//                resultList.add(future);
//            }
//            countDownLatch.await();
//            for (Future<MultiValueMap> future : resultList) {
//                allMultiValueMap.putAll(future.get());
//            }
//            final long end = System.currentTimeMillis();
//            System.out.println("多线程花费了" + (end - start) + "ms");
//        } finally {
//            pool.shutdown();
//        }
//    }

    @Test
    void test3() throws ClassNotFoundException, SQLException {
        final boolean execute;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            // 创建链接
            Connection connection = (Connection) DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/test?characterEncoding=utf-8&serverTimezone=Asia/Shanghai",
                    "root", "123456");

            final Statement statement = connection.createStatement();
            String sql = "USE aa";
            execute = statement.execute(sql);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
        }
        System.out.println(execute);

    }

    @Test
    void testCircle() throws ClassNotFoundException, SQLException {
        System.out.println(aa.getUsername());
    }

    @Test
    void testTraceIntegrate() throws ClassNotFoundException, SQLException {
        List<UniPoint> singleVarDataList=new ArrayList<>();
        for (int i = 0; i < 163840; i++) {
             UniPoint uniPoint = new UniPoint(new BigDecimal(i),new BigDecimal(i+1),"v2");
            singleVarDataList.add(uniPoint);
        }
        List<Object[]> dataObjArr = new ArrayList<>();
        Integer downSamplingRate=16384;
        int bucketSize = singleVarDataList.size() / downSamplingRate;
        singleVarDataList = LTThreeBuckets.sorted(singleVarDataList, bucketSize);
        dataObjArr= convertPojoList2ObjListArr(singleVarDataList, 4);
        final long start = System.currentTimeMillis();
//        for (int i = 0; i < 1000000; i++) {
//            Object[] obj = new Object[4];
//            obj[0] = "v0";
//            obj[1] = i;
//            obj[2] = i * 2;
//            obj[3] = 2;
//            dataObjArr.add(obj);
//        }
        String traceDownsamplingBatchSql = "INSERT INTO " + "trace_downsampling" + " (varName, `timestamp`, value, downSamplingRate) VALUES(?,?,?,?)";
        Connection connection = ConnectionManager.getConnection("trace");
        BaseUtils.executeDownsamplingBatchUpdate(connection, "trace2_downsampling", dataObjArr);
        final long end = System.currentTimeMillis();
        System.out.println("花费了" + (end - start));
    }

    @Test
    void testTraceIntegrate2() throws ClassNotFoundException, SQLException {
        List<UniPoint> singleVarDataList=new ArrayList<>();
        Integer[] data = new Integer[]{2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384};
        final long start = System.currentTimeMillis();
        for (int i = 0; i < 50000; i++) {
                UniPoint uniPoint = new UniPoint(new BigDecimal(i),new BigDecimal(i+1));
                singleVarDataList.add(uniPoint);
        }
        List<Object[]> dataObjArr;
        final List<String> list = acquireAllField(TraceDownsampling.class);
        for (Integer downSamplingRate : data) {
            int bucketSize = singleVarDataList.size() / downSamplingRate;
            List<UniPoint> downDataList = LTThreeBuckets.sorted(singleVarDataList, bucketSize);
            dataObjArr= convertPojoList2ObjListArr2(downDataList, list.size(), downSamplingRate);
            Connection connection = ConnectionManager.getConnection("trace");
            BaseUtils.executeDownsamplingBatchUpdate(connection, "trace7_downsampling_v9", dataObjArr);
        }
        singleVarDataList.clear();
        final long end = System.currentTimeMillis();
        System.out.println("花费了" + (end - start));
    }

    public static List<Object[]> convertPojoList2ObjListArr2(List<UniPoint> singleVarDataList, int size, Integer downsamplingRate) {
        List<Object[]> objects = new ArrayList<>();
        for (UniPoint uniPoint : singleVarDataList) {
            Object[] obj = new Object[size];
            obj[0] = uniPoint.getX();
            obj[1] = uniPoint.getY();
            obj[2] = downsamplingRate;
            objects.add(obj);
        }
        return objects;

    }


    @Test
    public void testOriginalDownsampling22() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        final Class<?> clazz = Class.forName(DOMAIN_PREFIX.concat("TraceDownsampling"));
        Object[] regionParam = new Object[]{ 0, 1000000};
//        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
//        MapSqlParameterSource parameters = new MapSqlParameterSource();
//        parameters.addValue("ids", new String[]{"v0","v2"});
        String param = "'v0','v1'";
        String originalRegionSql = "select * from trace1_downsampling_v6 where   timestamp between ? and ? ";

        //    String originalRegionSql = "select varName, timestamp, value from trace_downsampling where  downSamplingRate=? and timestamp between ? and ? and varName =? ";
        long start = System.currentTimeMillis();
        List list = jdbcTemplate.query(originalRegionSql, regionParam, new BeanPropertyRowMapper<>(clazz));
//        System.out.println(list);
        long end = System.currentTimeMillis();
        System.out.println("花费了" + (end - start) + "ms");//21ms
    }

    @Test
    void testNewDown() throws ClassNotFoundException, SQLException {
        List<Object[]> dataObjArr = new ArrayList<>();
        final long start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            Object[] obj = new Object[4];
            obj[0] = i;
            obj[1] = i;
            obj[2] = i * 2;
            dataObjArr.add(obj);
        }
        Connection connection = ConnectionManager.getConnection("trace");
        BaseUtils.executeDownsamplingBatchUpdate(connection, "trace1_downsampling_v0_2", dataObjArr);
        final long end = System.currentTimeMillis();
        System.out.println("花费了" + (end - start));
    }

    @Test
    void testTraceNewIntegrate() throws ClassNotFoundException, SQLException {
        List<UniPoint> singleVarDataList=new ArrayList<>();
        Integer[] data = new Integer[]{2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384};
        final long start = System.currentTimeMillis();
        for (int i = 0; i < 1000000; i++) {
            UniPoint uniPoint = new UniPoint(new BigDecimal(i),new BigDecimal(i+1));
            singleVarDataList.add(uniPoint);
        }
        List<Object[]> dataObjArr;
        final List<String> list = acquireAllField(TraceDownsampling.class);
        for (Integer downSamplingRate : data) {
            int bucketSize = singleVarDataList.size() / downSamplingRate;
            List<UniPoint> downDataList = LTThreeBuckets.sorted(singleVarDataList, bucketSize);
            dataObjArr= convertPojoList2ObjListArr2(downDataList, list.size(), downSamplingRate);
            Connection connection = ConnectionManager.getConnection("trace");
            BaseUtils.executeDownsamplingBatchUpdate(connection, "trace3_downsampling_v9".concat("_").concat(String.valueOf(downSamplingRate)), dataObjArr);
        }
        singleVarDataList.clear();
        final long end = System.currentTimeMillis();
        System.out.println("花费了" + (end - start));
    }

//    @Test
//    void testNewDownThread() throws ExecutionException, InterruptedException {
//        try {
//            Long reqStartTimestamp = 0L;
//            Long reqEndTimestamp = 1000000L;
//            Integer closestRate = 4096;
//            MultiValueMap allMultiValueMap = new MultiValueMap();
//            List<Future<MultiValueMap>> resultList = new ArrayList<>();
//            final long start = System.currentTimeMillis();
//            final List<String> varList = Arrays.asList("v0", "v1","v2","v3","v4","v5","v6","v7","v8","v9");
//            CountDownLatch countDownLatch = new CountDownLatch(varList.size());
//            for (String varName : varList) {
//                Future<MultiValueMap> future = pool.submit(new QueryEachDownsamplingTableHandler("trace3_downsampling".concat("_").concat(varName), reqStartTimestamp, reqEndTimestamp, jdbcTemplate, countDownLatch, varName, closestRate,10, null));
//                resultList.add(future);
//            }
//            countDownLatch.await();
//            for (Future<MultiValueMap> future : resultList) {
//                allMultiValueMap.putAll(future.get());
//            }
//            final long end = System.currentTimeMillis();
//            System.out.println("多线程花费了" + (end - start) + "ms");
//        } finally {
//            pool.shutdown();
//        }
//    }


    @Test
    void testTimestampStatistics() throws ExecutionException, InterruptedException {
      String s="select max(id) from trace15_downsampling_app00pou00var0_16384";
        final Integer i = jdbcTemplate.queryForObject(s, Integer.class);
        System.out.println(i);
//        Object[]param=new Object[]{165};
//        String s="select * from trace19_downsampling_app00pou00var1_8192 where id=?";
//        List<TraceDownsampling> traceDownsamplingList = jdbcTemplate.query(s, param, new BeanPropertyRowMapper<>(TraceDownsampling.class));
//        System.out.println(traceDownsamplingList);
    }

    @Test
    void testTraceIntegrate3() throws ClassNotFoundException, SQLException {
        List<UniPoint> singleVarDataList=new ArrayList<>();
        Integer[] data = new Integer[]{2};
        final long start = System.currentTimeMillis();
        for (int i = 0; i < 10000000; i++) {
            UniPoint uniPoint = new UniPoint(new BigDecimal(i),new BigDecimal(i+1));
            singleVarDataList.add(uniPoint);
        }
        List<Object[]> dataObjArr;
        final List<String> list = acquireAllField(TraceDownsampling.class);
        for (Integer downSamplingRate : data) {
            int bucketSize = singleVarDataList.size() / downSamplingRate;
            List<UniPoint> downDataList = LTThreeBuckets.sorted(singleVarDataList, bucketSize);
            dataObjArr= convertPojoList2ObjListArr2(downDataList, list.size(), downSamplingRate);
            Connection connection = ConnectionManager.getConnection("trace");
            BaseUtils.executeDownsamplingBatchUpdate(connection, "trace888888888888888_downsampling_v8_2", dataObjArr);
        }
        singleVarDataList.clear();
        final long end = System.currentTimeMillis();
        System.out.println("花费了" + (end - start));
    }

    @Test
    void testTraceSampleInteral() throws ClassNotFoundException, SQLException {
         TraceTableRelatedInfo traceTableRelatedInfo = traceTableRelatedInfoMapper.selectByPrimaryKey(684376347717L);
         String traceConfig = traceTableRelatedInfo.getTraceConfig();
         JSONObject jsonObject = (JSONObject) JSON.parse(traceConfig);
        final String config = jsonObject.get("config").toString();
        JSONObject childJsonObject = (JSONObject) JSON.parse(config);
         String sampleInterval = childJsonObject.get("sampleInterval").toString();
        System.out.println(Integer.valueOf(sampleInterval));
    }
    @Test
    void testcc() throws ClassNotFoundException, SQLException {
        String originalRegionCountSql = "select count(*),min(id),max(id) from trace76_0";
         List<TraceParamCount> traceParamCounts = jdbcTemplate.query(originalRegionCountSql, new RowMapper<TraceParamCount>() {
            @Override
            public TraceParamCount mapRow(ResultSet rs, int rowNum) throws SQLException {
                TraceParamCount traceParamCount = new TraceParamCount();
               // traceParamCount.setCount(rs.getInt(1));
                traceParamCount.setMin(rs.getLong(2));
                traceParamCount.setMax(rs.getLong(3));
                return traceParamCount;
            }
        });
        System.out.println(traceParamCounts);
    }
    @Test
    void testvolva() throws ClassNotFoundException, SQLException {
        Object[] regionParam = new Object[]{0, 20};
        String originalRegionSql = "select " + "*" + " from " + "trace81_0" + " where id between ? and ? ";
        // List list = jdbcTemplate.query(originalRegionSql, regionParam, new BeanPropertyRowMapper<>(clazz));
        final List<Map<String, Object>> list = jdbcTemplate.queryForList(originalRegionSql, regionParam);
        final List<UniPoint> uniPointList = convertList2Uni(list);
        System.out.println(uniPointList);
    }

    @Test
    void testfull(){
        final Pair<Long, Long> pair = getLastCanDownSamplingValue(7663725L, 10);
        System.out.println(pair);
    }

    @Test
    void testfull2(){
        final Long minValue = getMinValue(30598L, 1);
        System.out.println(minValue);
    }

    @Test
    void testfull3(){
//        final List<UniPoint> list = LTThreeBuckets.sorted(singleVarDataList, 1);
//        System.out.println(list);
//        final Set<String> aa1 = getQueryTable2(2100000L, 47890019L, "aa");
//        System.out.println(aa1);
    }

    //    @Test
//    private Set<String> getQueryTable2(Long reqStartTimestamp, Long reqEndTimestamp, String parentTable) {
//        Set<String> set = new TreeSet<>();
//        if (reqStartTimestamp > reqEndTimestamp) {
//            throw new RuntimeException("开始时间戳不能大于结束时间戳!");
//
//        }
//        if (reqStartTimestamp > (long) (totalSize / shardNum) * (shardNum - 1) * 10) {
//            set.add(parentTable.concat("_").concat(String.valueOf(shardNum - 1)));
//        }
//        if (reqEndTimestamp < ((totalSize / shardNum) * 10)) {
//            set.add(parentTable.concat("_").concat(String.valueOf(0)));
//        }
//        int beginSlot = (int) (reqStartTimestamp / ((totalSize / shardNum) * 10));
//        int endSlot = (int) (reqEndTimestamp / ((totalSize / shardNum) * 10));
//        //防止endTimestamp请求过大导致实际没有那么多分表
//        if (endSlot > shardNum - 1) {
//            endSlot = shardNum - 1;
//        }
//        for (int i = beginSlot; i <= endSlot; i++) {
//            set.add(parentTable.concat("_").concat(String.valueOf(i)));
//        }
//        return set;
//    }
    @Test
    void testDownsampling(){
         long start = System.currentTimeMillis();
        final int i = 100000 / 32;
        final List<UniPoint> uniPoints = LTThreeBuckets.sorted(singleVarDataList, i);
        if (uniPoints.size()>0){
            long end = System.currentTimeMillis();
            System.out.println(end-start);
        }
        System.out.println(uniPoints.size());

    }


    @Test
    void ccc(){
        final BigDecimal bigDecimal = getBigDecimal(1.34567f);
        System.out.println(bigDecimal);
    }

    @Test
    void testaa(){
        final long l = System.currentTimeMillis();
        Object[] otherRegionParam = new Object[]{0 + 1, 7375675 - 1};
        String originalRegionSql = "select " + "*" + " from " + "trace303_0" + " where id between ? and ? ";
        // List list = jdbcTemplate.query(originalRegionSql, regionParam, new BeanPropertyRowMapper<>(clazz));
        List<Map<String, Object>> list = jdbcTemplate.queryForList(originalRegionSql, otherRegionParam);
        System.out.println(System.currentTimeMillis()-l);
    }

    @Test
    void testaba() throws InterruptedException, ExecutionException {
        CountDownLatch lagCountDownLatch=new CountDownLatch(1);
        List<Future<List<UniPoint>>> lagResultList = new ArrayList<>();
        List<UniPoint> lagUniPointList=new ArrayList<>();
        final long l = System.currentTimeMillis();
        Future<List<UniPoint>> future = pool.submit(new LagFullTableHandler("trace303_0", 1L,  7375674L, jdbcTemplate, lagCountDownLatch, "App_00Pou_00var_00,App_00Pou_00var_01,App_00Pou_00var_02,App_00Pou_00var_03,App_00Pou_00var_04,App_00Pou_00var_05,App_00Pou_00var_06,App_00Pou_00var_07,App_00Pou_00var_08,App_00Pou_00var_09", null));
        lagResultList.add(future);
        lagCountDownLatch.await();
        for (Future<List<UniPoint>> aafuture : lagResultList) {
            lagUniPointList.addAll(aafuture.get());
        }
        System.out.println(System.currentTimeMillis()-l);
    }

    @Test
    void cc(){
        final Integer downsamplingRule = customDownsamplingRule(512, 213);
        System.out.println(downsamplingRule);
    }

    @Test
    void cursor(){
        jdbcTemplate.query(con -> {
            PreparedStatement preparedStatement =
                    con.prepareStatement("select * from table",
                            ResultSet.TYPE_FORWARD_ONLY,
                            ResultSet.CONCUR_READ_ONLY);
            preparedStatement.setFetchSize(Integer.MIN_VALUE);
            preparedStatement.setFetchDirection(ResultSet.FETCH_FORWARD);
            return preparedStatement;
        }, rs -> {
            while (rs.next()) {
                System.err.println(rs.getString("id"));
            }
        });

    }

    @Test
    void testBatchData() throws NoSuchFieldException, ExecutionException, InterruptedException, IllegalAccessException {
        List<Map<String, String>> mapList = new ArrayList<>();
        List<String> originalVarList=new ArrayList<>();
        originalVarList.add("plc_prgb0");
        originalVarList.add("plc_prgr0");
        originalVarList.add("plc_prgr1");
        originalVarList.add("plc_prgr2");
//        originalVarList.add("App_00.Pou_00.var_03");
//        originalVarList.add("App_00.Pou_00.var_04");
//        originalVarList.add("App_00.Pou_00.var_05");
//        originalVarList.add("App_00.Pou_00.var_06");
//        originalVarList.add("App_00.Pou_00.var_07");
//        originalVarList.add("App_00.Pou_00.var_08");
//        originalVarList.add("App_00.Pou_00.var_09");
        List<String> filterVarList = new ArrayList<>();
        for (String varName : originalVarList) {
            String filterVarName = erasePoint(varName);
            Map<String, String> innerMap = new HashMap<>();
            innerMap.put(filterVarName, varName);
            mapList.add(innerMap);
            filterVarList.add(filterVarName);
        }
        String fieldName = StringUtils.join(filterVarList, ",");
        final MultiValueMap allMultiValueMap = new MultiValueMap();
        int closestRate = 8;
        for (String varName : filterVarList) {
            Long[] samplingParam = new Long[]{0L, 3849582000L};
            String samplingSql = " select timestamp, value from " + "trace62_downsampling".concat("_").concat(varName).concat("_").concat(baseDownTableSuffix) + "  where  timestamp between ? and ?  ";
            List<TraceDownsampling> traceDownsamplingList = jdbcTemplate.query(samplingSql, samplingParam, new BeanPropertyRowMapper<>(TraceDownsampling.class));
            List<UniPoint> uniPointList = convertTraceDownsampling2UniPoint(traceDownsamplingList, varName);
            int gap = uniPointList.size() / 2000;
            if (gap > 1) {
                closestRate = getClosestRate(gap);
                int bucketSize = uniPointList.size() / closestRate;
                List<UniPoint> filterList = LTThreeBuckets.sorted(uniPointList, bucketSize);
                MultiValueMap multiValueMap = uniPoint2Map(filterList, mapList);
                allMultiValueMap.putAll(multiValueMap);
            } else if (gap == 1) {
                MultiValueMap multiValueMap = uniPoint2Map(uniPointList, mapList);
                allMultiValueMap.putAll(multiValueMap);
            }
        }
        System.out.println(allMultiValueMap);
      //  handleDownTailData(0L, 85440000L, "trace302", mapList, fieldName, allMultiValueMap, 10, Integer.parseInt(baseDownTableSuffix) * closestRate, filterVarList);
       // System.out.println("大数据量当前使用了{}降采样", Integer.parseInt(baseDownTableSuffix) * closestRate);
    }

    private void handleDownTailData(Long reqStartTimestamp, Long reqEndTimestamp, String currentTableName, List<Map<String, String>> mapList, String fieldName,
                                    MultiValueMap allMultiValueMap, int sampleInterval, int closestRate, List<String> filterVarList) throws NoSuchFieldException, IllegalAccessException, ExecutionException, InterruptedException {
        Long beginLeftStartTimestamp = null;
        Long endLeftStartTimestamp = null;
        final Set<Map.Entry<BigDecimal, BigDecimal>> entrySet = allMultiValueMap.entrySet();
        for (Map.Entry<BigDecimal, BigDecimal> entry : entrySet) {
            List valueList = (List) entry.getValue();
            BigDecimal[] startBd = (BigDecimal[]) valueList.get(0);
            /**
             * sampleInterval=10为例
             * 需要处理的情况:假如是8倍降采样，假如起始点是48833,去降采样表查询发现最开始是48910，因为上一个点是48830，小于起始点，不需要查询，此时48833-48910这块儿数据就没返回，因为48910-10大于起始点48833，所以需要处理
             * 不需要处理的情况:但是需要判断假如前面的处理第一条数据返回117850,起始点是117843，此时小于一个sampleInterval，不需要处理了
             */
            long threadStartValue = startBd[0].longValue();
            if (threadStartValue > reqStartTimestamp && whetherNeedHandleHeadData(threadStartValue, reqStartTimestamp, sampleInterval)) {
                beginLeftStartTimestamp = threadStartValue - 1;
            }
            BigDecimal[] endBd = (BigDecimal[]) valueList.get(valueList.size() - 1);
            final long threadLastValue = endBd[0].longValue();
            //最后那条数据如果等于结束值，就不用下面的查询剩余数据了
            if (threadLastValue != reqEndTimestamp) {
                endLeftStartTimestamp = threadLastValue + 1;
            }
            break;
        }
        if (beginLeftStartTimestamp != null) {
            handleTailOrHeadBusiness(reqStartTimestamp, beginLeftStartTimestamp, currentTableName, mapList, fieldName, allMultiValueMap, closestRate, filterVarList);
        }
        if (endLeftStartTimestamp != null) {
            handleTailOrHeadBusiness(endLeftStartTimestamp, reqEndTimestamp, currentTableName, mapList, fieldName, allMultiValueMap, closestRate, filterVarList);
        }
    }

    private void handleTailOrHeadBusiness(Long startTimestamp, Long endTimestamp, String currentTableName, List<Map<String, String>> mapList, String fieldName, MultiValueMap allMultiValueMap, int closestRate, List<String> filterVarList) throws NoSuchFieldException, IllegalAccessException, InterruptedException, ExecutionException {
        //小于一个任务周期就没必要请求了
        if (endTimestamp - startTimestamp < 10) {
            return;
        }
        Set<String> queryTable = getQueryTable(startTimestamp, endTimestamp, currentTableName);
        CountDownLatch lagCountDownLatch = new CountDownLatch(queryTable.size());
        List<Future<List<UniPoint>>> lagResultList = new ArrayList<>();
        List<UniPoint> lagUniPointList = new ArrayList<>();
        for (String table : queryTable) {
            Future<List<UniPoint>> future = pool.submit(new LagFullTableHandler(table, startTimestamp, endTimestamp, jdbcTemplate, lagCountDownLatch, fieldName, mapList));
            lagResultList.add(future);
        }
        lagCountDownLatch.await();
        for (Future<List<UniPoint>> future : lagResultList) {
            lagUniPointList.addAll(future.get());
        }
        for (String varName : filterVarList) {
            List<UniPoint> singleVarDataList = lagUniPointList.stream().filter(item -> varName.equals(item.getVarName())).toList();
            int bucketSize = singleVarDataList.size() > closestRate ? singleVarDataList.size() / closestRate : 0;
            Integer customDownsamplingRule = customDownsamplingRule(closestRate, singleVarDataList.size());
            if (bucketSize > 0) {//够一定数量进行降采样
                List<UniPoint> uniPoints = LTThreeBuckets.sorted(singleVarDataList, bucketSize);
                uniPoint2Map(uniPoints, allMultiValueMap, mapList);
            } else if (bucketSize == 0 && customDownsamplingRule != 1 && customDownsamplingRule <= singleVarDataList.size() && singleVarDataList.size() > 2) { //如果singleVarDataList数量大的话并且closestRate足够大bucketSize仍然可能为0，所以此时假如closestRate等于64，那么取次一级的32进行降采样
                List<UniPoint> uniPoints = LTThreeBuckets.sorted(singleVarDataList, singleVarDataList.size() / customDownsamplingRule);
                uniPoint2Map(uniPoints, allMultiValueMap, mapList);
            } else if (CollectionUtils.isNotEmpty(singleVarDataList) && bucketSize == 0) {//数量少的话直接返回全量表数据
                uniPoint2Map(lagUniPointList, allMultiValueMap, mapList);
                break;
            }
        }
    }

    private Set<String> getQueryTable(Long reqStartTimestamp, Long reqEndTimestamp, String parentTable) {
        Set<String> set = new TreeSet<>();
        if (reqStartTimestamp > reqEndTimestamp) {
            throw new RuntimeException("开始时间戳不能大于结束时间戳!");

        }
        if (reqStartTimestamp > (long) (totalSize / shardNum) * (shardNum - 1) *10) {
            set.add(parentTable.concat("_").concat(String.valueOf(shardNum - 1)));
        }
        if (reqEndTimestamp < ((long) (totalSize / shardNum) *10)) {
            set.add(parentTable.concat("_").concat(String.valueOf(0)));
        }
        int beginSlot = (int) (reqStartTimestamp / ((totalSize / shardNum) *10));
        int endSlot = (int) (reqEndTimestamp / ((totalSize / shardNum) *10));
        //防止endTimestamp请求过大导致实际没有那么多分表
        if (endSlot > shardNum - 1) {
            endSlot = shardNum - 1;
        }
        for (int i = beginSlot; i <= endSlot; i++) {
            set.add(parentTable.concat("_").concat(String.valueOf(i)));
        }
        return set;
    }

    @Test
    void testSingleData() throws NoSuchFieldException, IllegalAccessException {
        final List<String> originalVarList = new ArrayList<>();
        originalVarList.add("App_00.Pou_00.var_00");
        originalVarList.add("App_00.Pou_00.var_01");
        originalVarList.add("App_00.Pou_00.var_02");
        originalVarList.add("App_00.Pou_00.var_03");
        originalVarList.add("App_00.Pou_00.var_04");
        originalVarList.add("App_00.Pou_00.var_05");
        originalVarList.add("App_00.Pou_00.var_06");
        originalVarList.add("App_00.Pou_00.var_07");
        originalVarList.add("App_00.Pou_00.var_08");
        originalVarList.add("App_00.Pou_00.var_09");
        List<String> filterVarList = new ArrayList<>();
        List<Map<String, String>> mapList = new ArrayList<>();
        for (String varName : originalVarList) {
            String filterVarName = erasePoint(varName);
            Map<String, String> innerMap = new HashMap<>();
            innerMap.put(filterVarName, varName);
            mapList.add(innerMap);
            filterVarList.add(filterVarName);
        }
        String fieldName = StringUtils.join(filterVarList, ",");
        String allFieldName = VarConst.ID.concat(",").concat(fieldName);
        Object[] regionParam = new Object[]{4000040};
        String sql="select "+allFieldName+"  from trace8_1 where id=?";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, regionParam);
        MultiValueMap multiValueMap=convertList2MultiMap(list, mapList);
        //multiValueMap.
        System.out.println(multiValueMap.size());

    }

    @Test
    void aa(){
        final Integer downsamplingRule = getSpecificClosestRate(1);
        System.out.println(downsamplingRule);
        float f = (float)10767 / (float)6000;
        System.out.println(f);
        final int round = Math.round(f);
        System.out.println(round);
    }

    @Test
    void testgetNextDownRate(){
        final String nextDownRate = getNextDownRate("256");
        System.out.println(nextDownRate);
    }

    @Test
    void sub(){
        final List<List<UniPoint>> list = Lists.partition(singleVarDataList, 78);
        System.out.println(list.size());
    }

    @Test
    void testNotin(){
        List<Long>ids=new LinkedList<>();
        ids.add(1L);
        ids.add(3L);
        ids.add(7L);
        final List<TraceTableRelatedInfo> traceTableRelatedInfos =
                traceTableRelatedInfoMapper.disSelect(ids);
        System.out.println(traceTableRelatedInfos.size());

    }

    @Test
    void testTrace() {
        Object[] fullTableParam = new Object[]{123000L};
        String fullTableSql = "select " + "PLC_PRGr1" + " from " + "trace40_0" + " where id =? ";
        final List<Map<String, Object>> list = jdbcTemplate.queryForList(fullTableSql, fullTableParam);
        final Map<String, Object> lastMap = list.get(0);
        final Set<String> keySet = lastMap.keySet();
        for (String varName : keySet) {
            System.out.println(varName);
        }
        System.out.println(list);
    }

}
