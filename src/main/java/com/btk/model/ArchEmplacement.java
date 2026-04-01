package com.btk.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ARCH_EMPLACEMENT")
public class ArchEmplacement {

    @Id
    @Column(name = "ID_EMPLACEMENT")
    private Integer idEmplacement;

    @Column(name = "ETAGE", nullable = false)
    private Integer etage;

    @Column(name = "SALLE", nullable = false)
    private Integer salle;

    @Column(name = "RAYON", nullable = false)
    private Integer rayon;

    @Column(name = "RANGEE", nullable = false)
    private Integer rangee;

    @Column(name = "BOITE", nullable = false)
    private Integer boite;

    public Integer getIdEmplacement() { return idEmplacement; }
    public void setIdEmplacement(Integer idEmplacement) { this.idEmplacement = idEmplacement; }

    public Integer getEtage() { return etage; }
    public void setEtage(Integer etage) { this.etage = etage; }

    public Integer getSalle() { return salle; }
    public void setSalle(Integer salle) { this.salle = salle; }

    public Integer getRayon() { return rayon; }
    public void setRayon(Integer rayon) { this.rayon = rayon; }

    public Integer getRangee() { return rangee; }
    public void setRangee(Integer rangee) { this.rangee = rangee; }

    public Integer getBoite() { return boite; }
    public void setBoite(Integer boite) { this.boite = boite; }
}
