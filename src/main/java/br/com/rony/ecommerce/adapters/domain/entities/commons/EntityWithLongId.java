package br.com.rony.ecommerce.adapters.domain.entities.commons;

import jakarta.persistence.*;

@MappedSuperclass
public abstract class EntityWithLongId {
    private Long id;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
}
