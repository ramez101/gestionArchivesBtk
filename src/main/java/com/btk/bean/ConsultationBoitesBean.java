package com.btk.bean;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import com.btk.model.ArchDossier;
import com.btk.model.ArchEmplacement;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

@Named("consultationBoitesBean")
@ViewScoped
public class ConsultationBoitesBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static EntityManagerFactory emf;

    private String searchBoite;
    private List<ArchDossier> dossiers = Collections.emptyList();
    private ArchDossier selectedDossier;
    private boolean searched;
    private boolean resultLoaded;

    @PostConstruct
    public void init() {
    }

    public void search() {
        resetSearchResult();

        if (searchBoite == null || searchBoite.isBlank()) {
            addWarning("Veuillez choisir un numero de boite.");
            return;
        }

        Integer boite;
        try {
            boite = Integer.valueOf(searchBoite.trim());
        } catch (NumberFormatException e) {
            addWarning("Le numero de boite doit etre numerique.");
            return;
        }

        EntityManager em = getEMF().createEntityManager();
        try {
            dossiers = em.createQuery(
                            "select d from " + ArchDossier.class.getSimpleName() + " d " +
                                    "where d.idEmplacement in (" +
                                    "select e.idEmplacement from " + ArchEmplacement.class.getSimpleName() + " e " +
                                    "where e.boite = :boite" +
                                    ") order by d.pin, d.relation, d.idDossier",
                            ArchDossier.class)
                    .setParameter("boite", boite)
                    .getResultList();

            searched = true;
            resultLoaded = !dossiers.isEmpty();
        } finally {
            em.close();
        }
    }

    public void clear() {
        searchBoite = null;
        resetSearchResult();
    }

    private void resetSearchResult() {
        searched = false;
        dossiers = Collections.emptyList();
        selectedDossier = null;
        resultLoaded = false;
    }

    private void addWarning(String message) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_WARN, null, message));
    }

    private static synchronized EntityManagerFactory getEMF() {
        if (emf == null || !emf.isOpen()) {
            emf = Persistence.createEntityManagerFactory("btk");
        }
        return emf;
    }

    public String getSearchBoite() {
        return searchBoite;
    }

    public void setSearchBoite(String searchBoite) {
        this.searchBoite = searchBoite;
    }

    public List<ArchDossier> getDossiers() {
        return dossiers;
    }

    public ArchDossier getSelectedDossier() {
        return selectedDossier;
    }

    public void setSelectedDossier(ArchDossier selectedDossier) {
        this.selectedDossier = selectedDossier;
    }

    public boolean isSearched() {
        return searched;
    }

    public boolean isBoiteVide() {
        return searched && dossiers.isEmpty();
    }

    public boolean isResultLoaded() {
        return resultLoaded;
    }
}
