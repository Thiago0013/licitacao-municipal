package com.licitacao.municipal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; // 1. IMPORTAR

@SpringBootApplication
@EnableScheduling // 2. ADICIONAR AQUI
public class LicitacaoMunicipalApplication {
    public static void main(String[] args) {
        SpringApplication.run(LicitacaoMunicipalApplication.class, args);
    }
}