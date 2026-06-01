package com.licitacao.municipal.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Responsável por identificar o nome do município a partir do texto de um PDF.
 *
 * Estratégia em cascata (do mais específico ao mais genérico):
 *  1. Padrão "Município de X" / "Prefeitura de X" / "Prefeitura Municipal de X"
 *     → aplicado nos PRIMEIROS 3.000 caracteres do documento (cabeçalho)
 *  2. Padrão "X/CE" ou "X - CE" (UF do Ceará)
 *     → aplicado nos PRIMEIROS 3.000 caracteres do documento (cabeçalho)
 *  3. Lista curada dos 184 municípios do Ceará
 *     → busca nos PRIMEIROS 2.000 caracteres para não capturar municípios
 *        mencionados no corpo do texto como referência (ex: "caso similar em Granja")
 *
 * CORREÇÃO Bug 3:
 * A estratégia 3 anterior buscava em TODO o texto com contains() simples.
 * Se o PDF de Araripe mencionasse "Granja" em qualquer lugar do corpo (ex:
 * "processo anterior de Granja"), o sistema identificava "Granja" como o
 * município do caso em vez de "Araripe". Agora a busca é restrita ao início.
 */
@Service
@Slf4j
public class MunicipioExtractorService {

    // Quantos caracteres do início do texto analisar para as estratégias 1 e 2
    private static final int JANELA_CABECALHO = 3000;

    // Quantos caracteres do início usar na estratégia 3 (mais restrita)
    private static final int JANELA_LISTA = 2000;

    // Padrão 1: "Prefeitura (Municipal) de X" ou "Município de X"
    private static final Pattern PADRAO_PREFEITURA = Pattern.compile(
            "(?:Prefeitura(?:\\s+Municipal)?|Munic[íi]pio)\\s+de\\s+([A-ZÀ-Ú][a-zA-ZÀ-ú\\s]{2,40}?)(?=[,\\n\\r\\.\\(\\-]|\\s{2,}|$)",
            Pattern.MULTILINE | Pattern.UNICODE_CASE
    );

    // Padrão 2: "Nomecidade/CE" ou "Nomecidade - CE"
    private static final Pattern PADRAO_SIGLA_CE = Pattern.compile(
            "([A-ZÀ-Ú][a-zA-ZÀ-ú\\s]{2,40})\\s*(?:/|-)\\s*CE\\b",
            Pattern.UNICODE_CASE
    );

    // Lista curada dos 184 municípios do Ceará
    private static final List<String> MUNICIPIOS_CE = Arrays.asList(
        "Abaiara", "Acarapé", "Acaraú", "Acopiara", "Aiuaba", "Alcântaras",
        "Altaneira", "Alto Santo", "Amontada", "Antonina do Norte", "Apuiarés",
        "Aquiraz", "Aracati", "Aracoiaba", "Ararendá", "Araripe", "Aratuba",
        "Arneiroz", "Assaré", "Aurora", "Baixio", "Banabuiú", "Barbalha",
        "Barreira", "Barro", "Barroquinha", "Baturité", "Beberibe", "Bela Cruz",
        "Boa Viagem", "Brejo Santo", "Camocim", "Campos Sales", "Canindé",
        "Capistrano", "Caridade", "Cariré", "Caririaçu", "Cariús", "Carnaubal",
        "Cascavel", "Catarina", "Catunda", "Caucaia", "Cedro", "Chaval",
        "Choró", "Chorozinho", "Coreaú", "Crateús", "Crato", "Croatá",
        "Cruz", "Deputado Irapuan Pinheiro", "Ererê", "Eusébio", "Farias Brito",
        "Forquilha", "Fortaleza", "Fortim", "Frecheirinha", "General Sampaio",
        "Graça", "Granja", "Granjeiro", "Groaíras", "Guaiúba", "Guaraciaba do Norte",
        "Guaramiranga", "Hidrolândia", "Horizonte", "Ibaretama", "Ibiapina",
        "Ibicuitinga", "Icapuí", "Icó", "Iguatu", "Independência", "Ipaporanga",
        "Ipaumirim", "Ipu", "Ipueiras", "Iracema", "Irauçuba", "Itaiçaba",
        "Itaitinga", "Itapajé", "Itapipoca", "Itapiúna", "Itarema", "Itatira",
        "Jaguaretama", "Jaguaribara", "Jaguaribe", "Jaguaruana", "Jardim",
        "Jati", "Jijoca de Jericoacoara", "Juazeiro do Norte", "Jucás",
        "Lavras da Mangabeira", "Limoeiro do Norte", "Madalena", "Maracanaú",
        "Maranguape", "Marco", "Martinópole", "Massapê", "Mauriti", "Meruoca",
        "Milagres", "Milhã", "Miraíma", "Missão Velha", "Mombaça", "Monsenhor Tabosa",
        "Morada Nova", "Moraújo", "Morrinhos", "Mucambo", "Mulungu",
        "Nova Olinda", "Nova Russas", "Novo Oriente", "Ocara", "Orós",
        "Pacajus", "Pacatuba", "Pacoti", "Pacujá", "Palhano", "Palmácia",
        "Paracuru", "Paraipaba", "Parambu", "Paramoti", "Pedra Branca",
        "Penaforte", "Pentecoste", "Pereiro", "Pindoretama", "Piquet Carneiro",
        "Pires Ferreira", "Poranga", "Porteiras", "Potengi", "Potiretama",
        "Quiterianópolis", "Quixadá", "Quixelô", "Quixeramobim", "Quixeré",
        "Redenção", "Reriutaba", "Russas", "Saboeiro", "Salitre",
        "Santana do Acaraú", "Santana do Cariri", "Santa Quitéria", "São Benedito",
        "São Gonçalo do Amarante", "São João do Jaguaribe", "São Luís do Curu",
        "Senador Pompeu", "Senador Sá", "Sobral", "Solonópole", "Tabuleiro do Norte",
        "Tamboril", "Tarrafas", "Tauá", "Tejuçuoca", "Tianguá", "Trairi",
        "Tururu", "Ubajara", "Umirim", "Umari", "Uruburetama", "Uruoca",
        "Varjota", "Várzea Alegre", "Viçosa do Ceará"
    );

    public String extrairMunicipio(String textoPdf) {
        if (textoPdf == null || textoPdf.isBlank()) return null;

        // Estratégias 1 e 2: trabalham no cabeçalho do documento
        String cabecalho = textoPdf.substring(0, Math.min(textoPdf.length(), JANELA_CABECALHO));

        // Estratégia 1 — padrão "Prefeitura de X" / "Município de X"
        Matcher m1 = PADRAO_PREFEITURA.matcher(cabecalho);
        if (m1.find()) {
            String candidato = m1.group(1).trim();
            log.info("Município via padrão Prefeitura/Município: '{}'", candidato);
            return normalizar(candidato);
        }

        // Estratégia 2 — padrão "X/CE" ou "X - CE"
        Matcher m2 = PADRAO_SIGLA_CE.matcher(cabecalho);
        if (m2.find()) {
            String candidato = m2.group(1).trim();
            log.info("Município via padrão /CE: '{}'", candidato);
            return normalizar(candidato);
        }

        // Estratégia 3 — busca na lista dos 184 municípios
        // CORREÇÃO: restringe a busca aos primeiros JANELA_LISTA caracteres.
        // Antes buscava em todo o texto → se o PDF de Araripe mencionasse
        // "Granja" em qualquer parte do corpo, retornava "Granja".
        // Agora só considera o início do documento (onde fica o cabeçalho/identificação).
        String inicioTexto = textoPdf.substring(0, Math.min(textoPdf.length(), JANELA_LISTA));
        String inicioUpper = inicioTexto.toUpperCase();

        for (String municipio : MUNICIPIOS_CE) {
            if (inicioUpper.contains(municipio.toUpperCase())) {
                log.info("Município via lista curada (início do doc): '{}'", municipio);
                return municipio;
            }
        }

        log.warn("Município não identificado no PDF. RAG usará busca global.");
        return null;
    }

    private String normalizar(String nome) {
        return nome.replaceAll("[\\r\\n]+", " ").replaceAll("\\s{2,}", " ").trim();
    }
}
