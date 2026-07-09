package com.gamestore.upn.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rawg")
public class RawgController {
    @Value("${rawg.api.key:}")
    private String apiKey;

    @GetMapping("/buscar")
    public Map<String, Object> buscar(@RequestParam(required = false) String nombre) {
        if (nombre == null || nombre.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes enviar el nombre del juego.");
        }

        if (apiKey == null || apiKey.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta configurar RAWG API key en application.properties.");
        }

        try {
            String uri = UriComponentsBuilder
                    .fromHttpUrl("https://api.rawg.io/api/games")
                    .queryParam("search", nombre)
                    .queryParam("key", apiKey)
                    .queryParam("page_size", 1)
                    .build()
                    .toUriString();

            Map<?, ?> data = new RestTemplate().getForObject(uri, Map.class);
            List<?> results = data == null ? List.of() : (List<?>) data.get("results");
            if (results == null || results.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el juego en RAWG.");
            }

            Map<?, ?> primerJuego = (Map<?, ?>) results.get(0);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("nombre", primerJuego.get("name"));
            res.put("name", primerJuego.get("name"));
            res.put("imagenUrl", primerJuego.get("background_image"));
            res.put("background_image", primerJuego.get("background_image"));
            res.put("description_raw", "");
            return res;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al consultar RAWG.");
        }
    }
}
