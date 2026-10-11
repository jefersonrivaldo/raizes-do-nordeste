package br.com.raizes.domain;

import br.com.raizes.domain.Rules.Perfil;

public record Actor(long id, String nome, Perfil perfil, Long unidadeId) {}
