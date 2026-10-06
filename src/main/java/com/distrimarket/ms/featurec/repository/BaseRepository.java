package com.distrimarket.ms.featurec.repository;

import com.distrimarket.commons.entity.BaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface BaseRepository<E extends BaseEntity>
        extends JpaRepository<E, Long>, JpaSpecificationExecutor<E> {
}
