package com.li.lipicturecloud.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.li.lipicturecloud.mapper.PictureMapper;
import com.li.lipicturecloud.model.dto.space.analyze.SpaceCategoryAnalyzeRequest;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.service.PictureService;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpaceAnalyzeCategorySqlTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void categoryTotalsSurvivePreservedAndLowercaseJdbcLabels(boolean foldToLowercase) throws Exception {
        String url = "jdbc:h2:mem:category_" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=USER;"
                + (foldToLowercase ? "DATABASE_TO_LOWER=TRUE" : "DATABASE_TO_UPPER=FALSE");
        var dataSource = new UnpooledDataSource("org.h2.Driver", url, "sa", "");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("e2e-schema.sql"));
            statement.executeUpdate("INSERT INTO picture (id, url, name, userId, category, picSize, spaceId, isDelete) VALUES "
                    + "(1, '/one', 'one', 7, '旅行', 1024, NULL, 0),"
                    + "(2, '/two', 'two', 7, '旅行', 2048, NULL, 0),"
                    + "(3, '/three', 'three', 7, NULL, 512, NULL, 0),"
                    + "(4, '/private', 'private', 7, '旅行', 4096, 10, 0),"
                    + "(5, '/deleted', 'deleted', 7, '旅行', 128, NULL, 1)");
            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(false);
            configuration.setEnvironment(new Environment("analytics", new JdbcTransactionFactory(), dataSource));
            configuration.addMapper(PictureMapper.class);
            var factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
            try (var session = factory.openSession()) {
                PictureService pictures = mock(PictureService.class);
                when(pictures.getBaseMapper()).thenReturn(session.getMapper(PictureMapper.class));
                var service = new SpaceAnalyzeServiceImpl();
                ReflectionTestUtils.setField(service, "pictureService", pictures);
                var request = new SpaceCategoryAnalyzeRequest();
                request.setQueryPublic(true);
                assertThat(service.getSpaceCategoryAnalyze(request, new User()))
                        .extracting("category", "count", "totalSize")
                        .containsExactlyInAnyOrder(tuple("旅行", 2L, 3072L), tuple("未分类", 1L, 512L));
            }
        }
    }
}
