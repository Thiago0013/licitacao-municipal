package com.licitacao.municipal.controller;

import com.licitacao.municipal.model.Lei;
import com.licitacao.municipal.service.LeiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leis")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Permite requisições do seu painel Laravel/Front
public class LeiController {

    private final LeiService leiService;

    // CREATE
    @PostMapping
    public ResponseEntity<Lei> salvar(@RequestParam String titulo,
                                      @RequestParam String referencia,
                                      @RequestParam String conteudo) {
        Lei novaLei = leiService.cadastrarLei(titulo, referencia, conteudo);
        return ResponseEntity.ok(novaLei);
    }

    // READ ALL
    @GetMapping
    public ResponseEntity<List<Lei>> listarTodas() {
        return ResponseEntity.ok(leiService.listarTodas());
    }

    // READ ONE
    @GetMapping("/{id}")
    public ResponseEntity<Lei> buscarPorId(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(leiService.buscarPorId(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<Lei> atualizar(@PathVariable Long id,
                                         @RequestParam String titulo,
                                         @RequestParam String referencia,
                                         @RequestParam String conteudo) {
        try {
            Lei leiAtualizada = leiService.atualizarLei(id, titulo, referencia, conteudo);
            return ResponseEntity.ok(leiAtualizada);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        try {
            leiService.deletarLei(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}