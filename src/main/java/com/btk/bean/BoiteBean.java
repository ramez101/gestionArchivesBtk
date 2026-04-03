package com.btk.bean;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import com.btk.model.ArchEmplacement;
import com.btk.model.DossierEmp;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.SystemException;
import jakarta.transaction.UserTransaction;

@Named("boiteBean")
@ViewScoped
public class BoiteBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static EntityManagerFactory emf;

    @Resource
    private UserTransaction utx;

    private List<ArchEmplacement> emplacements = Collections.emptyList();

    private Integer etage;
    private Integer salle;
    private Integer rayon;
    private Integer rangee;
    private String newBoite;
    private String deleteBoite;

    private List<Integer> etages = Collections.emptyList();
    private List<Integer> salles = Collections.emptyList();
    private List<Integer> rayons = Collections.emptyList();
    private List<Integer> rangees = Collections.emptyList();

    @PostConstruct
    public void init() {
        loadEmplacements();
        etages = fetchDistinct("etage", null);
    }

    public void onEtageChange() {
        salle = null;
        rayon = null;
        rangee = null;

        salles = (etage == null) ? Collections.emptyList() : fetchDistinct("salle", "etage = :etage");
        rayons = Collections.emptyList();
        rangees = Collections.emptyList();
    }

    public void onSalleChange() {
        rayon = null;
        rangee = null;

        rayons = (salle == null) ? Collections.emptyList()
                : fetchDistinct("rayon", "etage = :etage and salle = :salle");
        rangees = Collections.emptyList();
    }

    public void onRayonChange() {
        rangee = null;

        rangees = (rayon == null) ? Collections.emptyList()
                : fetchDistinct("rangee", "etage = :etage and salle = :salle and rayon = :rayon");
    }

    public void addBoite() {
        if (etage == null || salle == null || rayon == null || rangee == null) {
            addError("Veuillez choisir etage, salle, rayon et rangee.");
            markValidationFailed();
            return;
        }

        Integer boiteValue = parseBoiteValue(newBoite);
        if (boiteValue == null) {
            addError("Le numero de boite doit etre numerique.");
            markValidationFailed();
            return;
        }

        EntityManager em = getEMF().createEntityManager();
        boolean txStarted = false;
        try {
            Long existing = em.createQuery(
                            "select count(e) from " + ArchEmplacement.class.getSimpleName() + " e where e.boite = :boite",
                            Long.class)
                    .setParameter("boite", boiteValue)
                    .getSingleResult();

            boolean existsInBoiteTable = archBoiteExists(em, boiteValue);

            if ((existing != null && existing > 0) || existsInBoiteTable) {
                addError("Boite deja existante.");
                markValidationFailed();
                return;
            }

            Integer maxId = em.createQuery(
                            "select max(e.idEmplacement) from " + ArchEmplacement.class.getSimpleName() + " e",
                            Integer.class)
                    .getSingleResult();
            int nextId = (maxId == null) ? 1 : maxId + 1;

            utx.begin();
            txStarted = true;
            em.joinTransaction();

            insertArchBoite(em, boiteValue);

            ArchEmplacement emplacement = new ArchEmplacement();
            emplacement.setIdEmplacement(nextId);
            emplacement.setEtage(etage);
            emplacement.setSalle(salle);
            emplacement.setRayon(rayon);
            emplacement.setRangee(rangee);
            emplacement.setBoite(boiteValue);

            em.persist(emplacement);
            em.flush();

            utx.commit();
            txStarted = false;

            addInfo("Ajout reussi.");
            resetAddForm();
            loadEmplacements();
        } catch (NotSupportedException | SystemException | RollbackException
                 | HeuristicMixedException | HeuristicRollbackException e) {
            if (txStarted) {
                try { utx.rollback(); } catch (Exception ignored) {}
            }
            addError("Erreur ajout boite : " + e.getMessage());
            markValidationFailed();
        } catch (RuntimeException e) {
            if (txStarted) {
                try { utx.rollback(); } catch (Exception ignored) {}
            }
            addError("Erreur ajout boite : " + e.getMessage());
            markValidationFailed();
        } finally {
            em.close();
        }
    }

    public void deleteBoite() {
        Integer boiteValue = parseBoiteValue(deleteBoite);
        if (boiteValue == null) {
            addError("Le numero de boite doit etre numerique.");
            markValidationFailed();
            return;
        }

        EntityManager em = getEMF().createEntityManager();
        boolean txStarted = false;
        try {
            Long existing = em.createQuery(
                            "select count(e) from " + ArchEmplacement.class.getSimpleName() + " e where e.boite = :boite",
                            Long.class)
                    .setParameter("boite", boiteValue)
                    .getSingleResult();

            boolean existsInBoiteTable = archBoiteExists(em, boiteValue);

            if ((existing == null || existing == 0) && !existsInBoiteTable) {
                addError("Boite inexistante.");
                markValidationFailed();
                return;
            }

            Long dossiersCount = em.createQuery(
                            "select count(de) from " + DossierEmp.class.getSimpleName() + " de " +
                                    "where de.boite = :boite",
                            Long.class)
                    .setParameter("boite", boiteValue)
                    .getSingleResult();

            if (dossiersCount != null && dossiersCount > 0) {
                addError("Tu ne peux pas supprimer cette boite, veuillez deplacer l'emplacement des dossiers d'abord.");
                markValidationFailed();
                return;
            }

            utx.begin();
            txStarted = true;
            em.joinTransaction();

            em.createQuery(
                            "delete from " + ArchEmplacement.class.getSimpleName() + " e where e.boite = :boite")
                    .setParameter("boite", boiteValue)
                    .executeUpdate();

            deleteArchBoite(em, boiteValue);

            utx.commit();
            txStarted = false;

            addWarn("Suppression reussie.");
            resetDeleteForm();
            resetAddForm();
            loadEmplacements();
        } catch (NotSupportedException | SystemException | RollbackException
                 | HeuristicMixedException | HeuristicRollbackException e) {
            if (txStarted) {
                try { utx.rollback(); } catch (Exception ignored) {}
            }
            addError("Erreur suppression boite : " + e.getMessage());
            markValidationFailed();
        } catch (RuntimeException e) {
            if (txStarted) {
                try { utx.rollback(); } catch (Exception ignored) {}
            }
            addError("Erreur suppression boite : " + e.getMessage());
            markValidationFailed();
        } finally {
            em.close();
        }
    }

    private Integer parseBoiteValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void loadEmplacements() {
        EntityManager em = getEMF().createEntityManager();
        try {
            emplacements = em.createQuery(
                            "select e from " + ArchEmplacement.class.getSimpleName() + " e " +
                                    "order by e.boite desc, e.etage, e.salle, e.rayon, e.rangee",
                            ArchEmplacement.class)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    private void resetAddForm() {
        etage = null;
        salle = null;
        rayon = null;
        rangee = null;
        newBoite = null;

        etages = fetchDistinct("etage", null);
        salles = Collections.emptyList();
        rayons = Collections.emptyList();
        rangees = Collections.emptyList();
    }

    private void resetDeleteForm() {
        deleteBoite = null;
    }

    private List<Integer> fetchDistinct(String field, String whereClause) {
        EntityManager em = getEMF().createEntityManager();
        try {
            StringBuilder jpql = new StringBuilder("select distinct e.")
                    .append(field)
                    .append(" from ")
                    .append(ArchEmplacement.class.getSimpleName())
                    .append(" e");

            if (whereClause != null && !whereClause.isBlank()) {
                jpql.append(" where ").append(whereClause);
            }

            jpql.append(" order by e.").append(field);

            var query = em.createQuery(jpql.toString(), Integer.class);

            if (whereClause != null && whereClause.contains(":etage")) {
                query.setParameter("etage", etage);
            }
            if (whereClause != null && whereClause.contains(":salle")) {
                query.setParameter("salle", salle);
            }
            if (whereClause != null && whereClause.contains(":rayon")) {
                query.setParameter("rayon", rayon);
            }

            return query.getResultList();
        } finally {
            em.close();
        }
    }

    private void addInfo(String message) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, message, null));
    }

    private void addWarn(String message) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_WARN, message, null));
    }

    private void addError(String message) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, message, null));
    }

    private void markValidationFailed() {
        FacesContext.getCurrentInstance().validationFailed();
    }

    private static synchronized EntityManagerFactory getEMF() {
        if (emf == null || !emf.isOpen()) {
            emf = Persistence.createEntityManagerFactory("btk");
        }
        return emf;
    }

    public List<ArchEmplacement> getEmplacements() { return emplacements; }

    public Integer getEtage() { return etage; }
    public void setEtage(Integer etage) { this.etage = etage; }

    public Integer getSalle() { return salle; }
    public void setSalle(Integer salle) { this.salle = salle; }

    public Integer getRayon() { return rayon; }
    public void setRayon(Integer rayon) { this.rayon = rayon; }

    public Integer getRangee() { return rangee; }
    public void setRangee(Integer rangee) { this.rangee = rangee; }

    public String getNewBoite() { return newBoite; }
    public void setNewBoite(String newBoite) { this.newBoite = newBoite; }

    public String getDeleteBoite() { return deleteBoite; }
    public void setDeleteBoite(String deleteBoite) { this.deleteBoite = deleteBoite; }

    public List<Integer> getEtages() { return etages; }
    public List<Integer> getSalles() { return salles; }
    public List<Integer> getRayons() { return rayons; }
    public List<Integer> getRangees() { return rangees; }

    private boolean archBoiteExists(EntityManager em, Integer boiteValue) {
        Object count = em.createNativeQuery("select count(1) from ARCH_BOITE where BOITE = :boite")
                .setParameter("boite", boiteValue)
                .getSingleResult();
        if (count instanceof Number) {
            return ((Number) count).longValue() > 0;
        }
        return false;
    }

    private void insertArchBoite(EntityManager em, Integer boiteValue) {
        em.createNativeQuery("insert into ARCH_BOITE (BOITE, DATE_CREATION) values (:boite, SYSDATE)")
                .setParameter("boite", boiteValue)
                .executeUpdate();
    }

    private void deleteArchBoite(EntityManager em, Integer boiteValue) {
        em.createNativeQuery("delete from ARCH_BOITE where BOITE = :boite")
                .setParameter("boite", boiteValue)
                .executeUpdate();
    }
}
