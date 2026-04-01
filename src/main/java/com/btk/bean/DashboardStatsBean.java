package com.btk.bean;

import java.io.Serializable;
import java.util.Date;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

@Named("dashboardStatsBean")
@RequestScoped
public class DashboardStatsBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static EntityManagerFactory emf;

    private long totalDossiers;
    private long totalBoites;
    private long totalDemandes;
    private long demandesEnAttente;
    private long demandesApprouvees;
    private long demandesRefusees;
    private long dossiersRestitues;
    private long totalUtilisateurs;
    private long utilisateursActifs;
    private long utilisateursInactifs;
    private Date refreshedAt;

    @PostConstruct
    public void init() {
        load();
    }

    public void load() {
        EntityManager em = getEMF().createEntityManager();
        try {
            totalDossiers = count(em, "SELECT COUNT(*) FROM ARCH_DOSSIER");
            totalBoites = count(em, "SELECT COUNT(*) FROM ARCH_BOITE");
            totalDemandes = count(em, "SELECT COUNT(*) FROM DEMANDE_DOSSIER");

            demandesEnAttente = count(em,
                    "SELECT COUNT(*) FROM DEMANDE_DOSSIER " +
                    "WHERE DATE_APPROUVE IS NULL AND DATE_RESTITUTION IS NULL");
            demandesApprouvees = count(em,
                    "SELECT COUNT(*) FROM DEMANDE_DOSSIER " +
                    "WHERE DATE_APPROUVE IS NOT NULL AND DATE_RESTITUTION IS NULL");
            demandesRefusees = count(em,
                    "SELECT COUNT(*) FROM DEMANDE_DOSSIER " +
                    "WHERE DATE_APPROUVE IS NULL AND DATE_RESTITUTION IS NOT NULL");
            dossiersRestitues = count(em,
                    "SELECT COUNT(*) FROM DEMANDE_DOSSIER " +
                    "WHERE DATE_APPROUVE IS NOT NULL AND DATE_RESTITUTION IS NOT NULL");

            totalUtilisateurs = count(em, "SELECT COUNT(*) FROM ARCH_UTILISATEURS");
            if (hasActiveColumn(em)) {
                utilisateursActifs = count(em,
                        "SELECT COUNT(*) FROM ARCH_UTILISATEURS " +
                        "WHERE UPPER(TRIM(NVL(TO_CHAR(ACTIVE), '1'))) " +
                        "IN ('1', 'TRUE', 'Y', 'YES', 'O', 'OUI', 'ACTIF', 'ACTIVE')");
                utilisateursInactifs = Math.max(0L, totalUtilisateurs - utilisateursActifs);
            } else {
                utilisateursActifs = totalUtilisateurs;
                utilisateursInactifs = 0L;
            }

            refreshedAt = new Date();
        } finally {
            em.close();
        }
    }

    private long count(EntityManager em, String sql) {
        try {
            Object raw = em.createNativeQuery(sql).getSingleResult();
            if (raw instanceof Number) {
                return ((Number) raw).longValue();
            }
            if (raw != null) {
                return Long.parseLong(String.valueOf(raw));
            }
            return 0L;
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    private boolean hasActiveColumn(EntityManager em) {
        long count = count(em,
                "SELECT COUNT(*) FROM USER_TAB_COLUMNS " +
                "WHERE TABLE_NAME = 'ARCH_UTILISATEURS' AND COLUMN_NAME = 'ACTIVE'");
        return count > 0L;
    }

    private static synchronized EntityManagerFactory getEMF() {
        if (emf == null || !emf.isOpen()) {
            emf = Persistence.createEntityManagerFactory("btk");
        }
        return emf;
    }

    public long getTotalDossiers() {
        return totalDossiers;
    }

    public long getTotalBoites() {
        return totalBoites;
    }

    public long getTotalDemandes() {
        return totalDemandes;
    }

    public long getDemandesEnAttente() {
        return demandesEnAttente;
    }

    public long getDemandesApprouvees() {
        return demandesApprouvees;
    }

    public long getDemandesRefusees() {
        return demandesRefusees;
    }

    public long getDossiersRestitues() {
        return dossiersRestitues;
    }

    public long getTotalUtilisateurs() {
        return totalUtilisateurs;
    }

    public long getUtilisateursActifs() {
        return utilisateursActifs;
    }

    public long getUtilisateursInactifs() {
        return utilisateursInactifs;
    }

    public long getDemandesTraitees() {
        return demandesApprouvees + demandesRefusees + dossiersRestitues;
    }

    public int getTauxTraitement() {
        if (totalDemandes <= 0L) {
            return 0;
        }
        double ratio = ((double) getDemandesTraitees() / (double) totalDemandes) * 100d;
        return (int) Math.round(ratio);
    }

    public Date getRefreshedAt() {
        return refreshedAt;
    }
}
