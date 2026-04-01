package com.btk.bean;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.btk.model.ArchDossier;

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
import org.primefaces.PrimeFaces;

@Named("modifierArchivesBean")
@ViewScoped
public class ModifierArchivesBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static EntityManagerFactory emf;

    @Resource
    private UserTransaction utx;

    private String searchType = "pin";
    private String searchValue;

    private Long dossierId;
    private Integer emplacementId;
    private boolean resultLoaded;

    private String portefeuille;
    private String pin;
    private String relation;
    private String charge;
    private String typeArchive;
    private String filialeId;
    private Integer etage;
    private Integer salle;
    private Integer rayon;
    private Integer rangee;
    private Integer boite;

    private List<Integer> etages = Collections.emptyList();
    private List<Integer> salles = Collections.emptyList();
    private List<Integer> rayons = Collections.emptyList();
    private List<Integer> rangees = Collections.emptyList();
    private List<Integer> boites = Collections.emptyList();

    public void search() {
        clearResult();

        if (searchValue == null || searchValue.isBlank()) {
            addWarn("Veuillez saisir un pin ou une relation.");
            return;
        }

        EntityManager em = getEMF().createEntityManager();
        try {
            boolean searchByRelation = "relation".equalsIgnoreCase(searchType);
            String searchedField = searchByRelation ? "relation" : "pin";

            List<ArchDossier> rows = em.createQuery(
                            "select d from " + ArchDossier.class.getSimpleName() + " d " +
                                    "where upper(trim(d." + searchedField + ")) = :value " +
                                    "order by d.idDossier",
                            ArchDossier.class)
                    .setParameter("value", normalizeSearchValue(searchValue))
                    .setMaxResults(1)
                    .getResultList();

            if (rows.isEmpty()) {
                PrimeFaces.current().executeScript("PF('modifierNotFoundDialog').show()");
                return;
            }

            ArchDossier dossier = rows.get(0);
            dossierId = dossier.getIdDossier();
            emplacementId = dossier.getIdEmplacement();
            portefeuille = dossier.getPortefeuille();
            pin = dossier.getPin();
            relation = dossier.getRelation();
            charge = dossier.getCharge();
            typeArchive = dossier.getTypeArchive();
            filialeId = dossier.getIdFiliale();
            resultLoaded = true;

            if (emplacementId != null) {
                var emp = em.find(com.btk.model.ArchEmplacement.class, emplacementId);
                if (emp != null) {
                    etage = emp.getEtage();
                    salle = emp.getSalle();
                    rayon = emp.getRayon();
                    rangee = emp.getRangee();
                    boite = emp.getBoite();
                }
            }

            loadEmplacementLists(em);

            PrimeFaces.current().executeScript("PF('modifierNotFoundDialog').hide()");
        } finally {
            em.close();
        }
    }

    public void save() {
        if (dossierId == null) {
            addWarn("Veuillez rechercher un dossier avant de confirmer.");
            return;
        }

        EntityManager em = getEMF().createEntityManager();
        boolean txStarted = false;
        try {
            Integer newEmplacementId = findEmplacementId(em);
            if (newEmplacementId == null) {
                addError("Emplacement introuvable.");
                markValidationFailed();
                return;
            }

            utx.begin();
            txStarted = true;
            em.joinTransaction();

            ArchDossier dossier = em.find(ArchDossier.class, dossierId);
            if (dossier == null) {
                addError("Dossier introuvable.");
                markValidationFailed();
                return;
            }

            dossier.setPortefeuille(portefeuille);
            dossier.setPin(pin);
            dossier.setRelation(relation);
            dossier.setCharge(charge);
            dossier.setTypeArchive(typeArchive);
            dossier.setIdFiliale(filialeId);
            dossier.setIdEmplacement(newEmplacementId);

            em.flush();

            utx.commit();
            txStarted = false;

            addInfo("Modification enregistree.");
            clear();
        } catch (NotSupportedException | SystemException | RollbackException
                 | HeuristicMixedException | HeuristicRollbackException e) {
            if (txStarted) {
                try { utx.rollback(); } catch (Exception ignored) {}
            }
            addError("Erreur modification : " + e.getMessage());
            markValidationFailed();
        } catch (RuntimeException e) {
            if (txStarted) {
                try { utx.rollback(); } catch (Exception ignored) {}
            }
            addError("Erreur modification : " + e.getMessage());
            markValidationFailed();
        } finally {
            em.close();
        }
    }

    public void clear() {
        searchType = "pin";
        searchValue = null;
        clearResult();
    }

    private void clearResult() {
        dossierId = null;
        emplacementId = null;
        resultLoaded = false;
        portefeuille = null;
        pin = null;
        relation = null;
        charge = null;
        typeArchive = null;
        filialeId = null;
        etage = null;
        salle = null;
        rayon = null;
        rangee = null;
        boite = null;

        etages = fetchDistinct(null, "etage");
        salles = Collections.emptyList();
        rayons = Collections.emptyList();
        rangees = Collections.emptyList();
        boites = Collections.emptyList();
    }

    private String normalizeSearchValue(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    public void onEtageChange() {
        salle = null;
        rayon = null;
        rangee = null;
        boite = null;

        salles = fetchDistinct("etage = :etage", "salle");
        rayons = Collections.emptyList();
        rangees = Collections.emptyList();
        boites = Collections.emptyList();
    }

    public void onSalleChange() {
        rayon = null;
        rangee = null;
        boite = null;

        rayons = fetchDistinct("etage = :etage and salle = :salle", "rayon");
        rangees = Collections.emptyList();
        boites = Collections.emptyList();
    }

    public void onRayonChange() {
        rangee = null;
        boite = null;

        rangees = fetchDistinct("etage = :etage and salle = :salle and rayon = :rayon", "rangee");
        boites = Collections.emptyList();
    }

    public void onRangeeChange() {
        boite = null;
        boites = fetchDistinct("etage = :etage and salle = :salle and rayon = :rayon and rangee = :rangee", "boite");
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

    private Integer findEmplacementId(EntityManager em) {
        if (etage == null || salle == null || rayon == null || rangee == null || boite == null) {
            return null;
        }
        List<Integer> ids = em.createQuery(
                        "select e.idEmplacement from com.btk.model.ArchEmplacement e " +
                                "where e.etage = :etage and e.salle = :salle and e.rayon = :rayon " +
                                "and e.rangee = :rangee and e.boite = :boite",
                        Integer.class)
                .setParameter("etage", etage)
                .setParameter("salle", salle)
                .setParameter("rayon", rayon)
                .setParameter("rangee", rangee)
                .setParameter("boite", boite)
                .setMaxResults(1)
                .getResultList();

        return ids.isEmpty() ? null : ids.get(0);
    }

    private void loadEmplacementLists(EntityManager em) {
        etages = em.createQuery("select distinct e.etage from com.btk.model.ArchEmplacement e order by e.etage",
                Integer.class).getResultList();

        salles = (etage == null) ? Collections.emptyList()
                : fetchDistinct(em, "etage = :etage", "salle");
        rayons = (salle == null) ? Collections.emptyList()
                : fetchDistinct(em, "etage = :etage and salle = :salle", "rayon");
        rangees = (rayon == null) ? Collections.emptyList()
                : fetchDistinct(em, "etage = :etage and salle = :salle and rayon = :rayon", "rangee");
        boites = (rangee == null) ? Collections.emptyList()
                : fetchDistinct(em, "etage = :etage and salle = :salle and rayon = :rayon and rangee = :rangee", "boite");
    }

    private List<Integer> fetchDistinct(String whereClause, String field) {
        EntityManager em = getEMF().createEntityManager();
        try {
            return fetchDistinct(em, whereClause, field);
        } finally {
            em.close();
        }
    }

    private List<Integer> fetchDistinct(EntityManager em, String whereClause, String field) {
        StringBuilder jpql = new StringBuilder("select distinct e.")
                .append(field)
                .append(" from com.btk.model.ArchEmplacement e");

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
        if (whereClause != null && whereClause.contains(":rangee")) {
            query.setParameter("rangee", rangee);
        }

        return query.getResultList();
    }

    private static synchronized EntityManagerFactory getEMF() {
        if (emf == null || !emf.isOpen()) {
            emf = Persistence.createEntityManagerFactory("btk");
        }
        return emf;
    }

    public String getSearchType() { return searchType; }
    public void setSearchType(String searchType) { this.searchType = searchType; }

    public String getSearchValue() { return searchValue; }
    public void setSearchValue(String searchValue) { this.searchValue = searchValue; }

    public boolean isResultLoaded() { return resultLoaded; }

    public String getPortefeuille() { return portefeuille; }
    public void setPortefeuille(String portefeuille) { this.portefeuille = portefeuille; }

    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }

    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }

    public String getCharge() { return charge; }
    public void setCharge(String charge) { this.charge = charge; }

    public String getTypeArchive() { return typeArchive; }
    public void setTypeArchive(String typeArchive) { this.typeArchive = typeArchive; }

    public String getFilialeId() { return filialeId; }
    public void setFilialeId(String filialeId) { this.filialeId = filialeId; }

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

    public List<Integer> getEtages() { return etages; }
    public List<Integer> getSalles() { return salles; }
    public List<Integer> getRayons() { return rayons; }
    public List<Integer> getRangees() { return rangees; }
    public List<Integer> getBoites() { return boites; }
}
