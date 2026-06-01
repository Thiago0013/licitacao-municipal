package com.licitacao.municipal.repository;

import com.licitacao.municipal.model.Analise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnaliseRepository extends JpaRepository<Analise, Long> {
    List<Analise> findAllByOrderByDataAnaliseDesc();
}
