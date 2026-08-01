package com.li.lipicturecloud.repository.mybatis;

import com.li.lipicturecloud.mapper.PictureMapper;
import com.li.lipicturecloud.model.entity.Picture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MybatisPictureRepositoryTest {

    private final PictureMapper mapper = mock(PictureMapper.class);
    private final MybatisPictureRepository repository = new MybatisPictureRepository(mapper);

    @Test
    void translatesMapperRowCountsToRepositoryResults() {
        Picture picture = new Picture();
        picture.setId(12L);
        when(mapper.insert(picture)).thenReturn(1);
        when(mapper.updateById(picture)).thenReturn(0);
        when(mapper.deleteById(12L)).thenReturn(1);

        assertThat(repository.add(picture)).isTrue();
        assertThat(repository.update(picture)).isFalse();
        assertThat(repository.removeById(12L)).isTrue();
    }

    @Test
    void wrapsNullableLookupInOptional() {
        Picture picture = new Picture();
        when(mapper.selectById(1L)).thenReturn(picture);
        when(mapper.selectById(2L)).thenReturn(null);

        assertThat(repository.findById(1L)).containsSame(picture);
        assertThat(repository.findById(2L)).isEmpty();
    }
}
