package com.btk.bean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.btk.model.ArchDossier;
import com.btk.model.ArchEmplacement;
import com.btk.util.DossierEmpUtil;

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
    private boolean resultLoaded;

    private String portefeuille;
    private String pin;
    private String relation;
    private String charge;
    private String typeArchive;
    private String filialeId;
    private Integer boite;
    private Integer boiteToRemove;
    private List<Integer> selectedBoites = new ArrayList<>();

    private List<Integer> boites = Collections.emptyList();

    @PostConstruct
    public void init() {
        boites = fetchBoites();
    }

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
            portefeuille = dossier.getPortefeuille();
            pin = dossier.getPin();
            relation = dossier.getRelation();
            charge = dossier.getCharge();
            typeArchive = dossier.getTypeArchive();
            filialeId = dossier.getIdFiliale();
            resultLoaded = true;

            selectedBoites = new ArrayList<>(DossierEmpUtil.findBoitesByDossierId(em, dossierId));
            boite = null;
            boiteToRemove = null;
            boites = fetchBoites();

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
            List<Integer> boitesToSave = DossierEmpUtil.normalizeBoites(resolveBoitesToSave());
            if (boitesToSave.isEmpty()) {
                addWarn("Ajouter au moins une boite.");
                markValidationFailed();
                return;
            }

            for (Integer boiteValue : boitesToSave) {
                if (!DossierEmpUtil.boiteExists(em, boiteValue)) {
                    addError("La boite " + boiteValue + " est introuvable.");
                    markValidationFailed();
                    return;
                }
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

            DossierEmpUtil.replaceBoites(em, dossierId, pin, relation, boitesToSave);
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
        resultLoaded = false;
        portefeuille = null;
        pin = null;
        relation = null;
        charge = null;
        typeArchive = null;
        filialeId = null;
        boite = null;
        boiteToRemove = null;
        selectedBoites = new ArrayList<>();
        boites = fetchBoites();
    }

    private String normalizeSearchValue(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    public List<Integer> completeBoite(String query) {
        if (boites == null || boites.isEmpty()) {
            return Collections.emptyList();
        }

        String term = query == null ? "" : query.trim();
        if (term.isBlank()) {
            return boites;
        }

        List<Integer> result = new ArrayList<>();
        for (Integer item : boites) {
            if (item == null) {
                continue;
            }
            if (String.valueOf(item).contains(term)) {
                result.add(item);
            }
        }
        return result;
    }

    public void addBoiteSelection() {
        if (boite == null) {
            addWarn("Saisir un numero de boite avant d'ajouter.");
            markValidationFailed();
            return;
        }

        if (boites == null || !boites.contains(boite)) {
            addError("La boite " + boite + " est introuvable.");
            markValidationFailed();
            return;
        }

        if (selectedBoites.contains(boite)) {
            addWarn("La boite " + boite + " est deja associee.");
            boite = null;
            markValidationFailed();
            return;
        }

        selectedBoites.add(boite);
        selectedBoites = new ArrayList<>(DossierEmpUtil.normalizeBoites(selectedBoites));
        addInfo("Boite " + boite + " ajoutee.");
        boite = null;
    }

    public void removeBoiteSelection() {
        if (boiteToRemove == null) {
            return;
        }

        selectedBoites.remove(boiteToRemove);
        addInfo("Boite " + boiteToRemove + " retiree.");
        boiteToRemove = null;
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

    private List<Integer> resolveBoitesToSave() {
        List<Integer> resolved = new ArrayList<>(selectedBoites);
        if (boite != null && !resolved.contains(boite)) {
            resolved.add(boite);
        }
        return resolved;
    }

    private List<Integer> fetchBoites() {
        EntityManager em = getEMF().createEntityManager();
        try {
            return em.createQuery(
                            "select distinct e.boite from " + ArchEmplacement.class.getSimpleName() + " e " +
                                    "order by e.boite",
                            Integer.class)
                    .getResultList();
        } finally {
            em.close();
        }
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

    public Integer getBoite() { return boite; }
    public void setBoite(Integer boite) { this.boite = boite; }

    public List<Integer> getBoites() { return boites; }
    public List<Integer> getSelectedBoites() { return selectedBoites; }
    public Integer getBoiteToRemove() { return boiteToRemove; }
    public void setBoiteToRemove(Integer boiteToRemove) { this.boiteToRemove = boiteToRemove; }
    public String getBoitesSummary() { return DossierEmpUtil.formatBoites(resolveBoitesToSave()); }
}
