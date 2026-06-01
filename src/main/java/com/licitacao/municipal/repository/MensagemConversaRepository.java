package com.licitacao.municipal.repository;

import com.licitacao.municipal.model.MensagemConversa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MensagemConversaRepository extends JpaRepository<MensagemConversa, Long> {

    List<MensagemConversa> findByAnaliseIdOrderByTimestampAsc(Long analiseId);
}
