package com.btk.bean;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.btk.model.ArchDossier;
import com.btk.model.ArchEmplacement;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.primefaces.PrimeFaces;

@Named("consultationArchivesBean")
@ViewScoped
public class ConsultationArchivesBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private static EntityManagerFactory emf;

    private String searchType = "pin";
    private String searchValue;
    private String searchPinValue;
    private String searchRelationValue;

    private Long idDossier;
    private String portefeuille;
    private String pin;
    private String relation;
    private String charge;
    private String typeArchive;
    private String filiale;
    private Integer etage;
    private Integer salle;
    private Integer rayon;
    private Integer rangee;
    private Integer boite;
    private boolean resultLoaded;
    private List<ScannedDocumentRow> scannedDocuments = Collections.emptyList();
    private List<TransmissionHistoryRow> transmissionHistory = Collections.emptyList();

    public void search() {
        clearResult();

        String effectiveValue = resolveSearchValue();
        if (effectiveValue == null || effectiveValue.isBlank()) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_WARN, "Veuillez saisir un pin ou une relation.", null));
            return;
        }

        EntityManager em = getEMF().createEntityManager();
        try {
            boolean searchByRelation = "relation".equalsIgnoreCase(searchType);
            String searchedField = searchByRelation ? "relation" : "pin";

            var query = em.createQuery(
                            "select d.idDossier, d.portefeuille, d.pin, d.relation, d.charge, d.typeArchive, d.idFiliale, " +
                                    "e.etage, e.salle, e.rayon, e.rangee, e.boite " +
                                    "from " + ArchDossier.class.getSimpleName() + " d, " +
                                    ArchEmplacement.class.getSimpleName() + " e " +
                                    "where d.idEmplacement = e.idEmplacement " +
                                    "and upper(trim(d." + searchedField + ")) = :searchValue " +
                                    "order by d.idDossier",
                            Object[].class)
                    .setParameter("searchValue", normalizeSearchValue(effectiveValue))
                    .setMaxResults(1);

            List<Object[]> rows = query.getResultList();

            if (rows.isEmpty()) {
                PrimeFaces.current().executeScript("PF('archiveNotFoundDialog').show()");
                return;
            }

            Object[] row = rows.get(0);
            idDossier = row[0] instanceof Number ? ((Number) row[0]).longValue() : null;
            portefeuille = (String) row[1];
            pin = (String) row[2];
            relation = (String) row[3];
            charge = (String) row[4];
            typeArchive = toTypeArchiveLabel((String) row[5]);
            filiale = toFilialeLabel((String) row[6]);
            etage = (Integer) row[7];
            salle = (Integer) row[8];
            rayon = (Integer) row[9];
            rangee = (Integer) row[10];
            boite = (Integer) row[11];
            resultLoaded = true;
            scannedDocuments = Collections.emptyList();

            PrimeFaces.current().executeScript("PF('archiveNotFoundDialog').hide()");
        } finally {
            em.close();
        }
    }

    public void clear() {
        searchType = "pin";
        searchValue = null;
        searchPinValue = null;
        searchRelationValue = null;
        clearResult();
        PrimeFaces.current().executeScript("PF('archiveNotFoundDialog').hide()");
    }

    public void loadScannedDocuments() {
        scannedDocuments = Collections.emptyList();
        if (!resultLoaded || idDossier == null) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_WARN, "Aucun dossier selectionne.", null));
            return;
        }

        String dossierName = buildDossierName();
        EntityManager em = getEMF().createEntityManager();
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery(
                            "select DOCUMENTS, NOM_DOSSIER, PATH_DOSSIER, UTILISATEUR_CREE, DATE_CREATION " +
                                    "from ARCH_DOCUMENT " +
                                    "where upper(NOM_DOSSIER) = :nom " +
                                    "order by ID_DOCUMENT")
                    .setParameter("nom", dossierName.toUpperCase(Locale.ROOT))
                    .getResultList();

            if (rows.isEmpty()) {
                FacesContext.getCurrentInstance().addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_INFO, "Aucun document scanne pour ce client.", null));
                return;
            }

            String dossierPath = toStringValue(rows.get(0)[2]);
            Map<String, DocMeta> metadataByFile = readMetadata(Paths.get(dossierPath));

            List<ScannedDocumentRow> loaded = new ArrayList<>(rows.size());
            for (Object[] row : rows) {
                String fileName = toStringValue(row[0]);
                DocMeta meta = metadataByFile.get(fileName.toLowerCase(Locale.ROOT));
                String nomDocument = meta == null || meta.nomDocument.isBlank() ? fileName : meta.nomDocument;
                loaded.add(new ScannedDocumentRow(
                        fileName,
                        nomDocument,
                        meta == null ? "" : meta.copie,
                        meta == null ? "" : meta.nombre,
                        meta == null ? "" : meta.description,
                        toStringValue(row[1]),
                        toStringValue(row[2]),
                        toStringValue(row[3]),
                        toDateValue(row[4])
                ));
            }
            scannedDocuments = loaded;
        } finally {
            em.close();
        }
    }

    public void loadTransmissionHistory() {
        transmissionHistory = Collections.emptyList();
        if (!resultLoaded || pin == null || pin.isBlank()) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_WARN, "Aucun dossier selectionne.", null));
            return;
        }

        EntityManager em = getEMF().createEntityManager();
        try {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery(
                            "SELECT ID_DEMANDE, EMETTEUR, RECEPTEUR, DATE_APPROUVE, DATE_RESTITUTION " +
                                    "FROM DEMANDE_DOSSIER " +
                                    "WHERE UPPER(TRIM(PIN)) = :pin " +
                                    "ORDER BY DATE_APPROUVE DESC NULLS LAST, DATE_RESTITUTION DESC NULLS LAST, ID_DEMANDE DESC")
                    .setParameter("pin", normalizeSearchValue(pin))
                    .getResultList();

            List<TransmissionHistoryRow> loaded = new ArrayList<>(rows.size());
            for (Object[] row : rows) {
                loaded.add(new TransmissionHistoryRow(
                        row[0] instanceof Number ? ((Number) row[0]).longValue() : null,
                        toStringValue(row[1]),
                        toStringValue(row[2]),
                        toDateValue(row[3]),
                        toDateValue(row[4])
                ));
            }
            transmissionHistory = loaded;
        } finally {
            em.close();
        }
    }

    private Map<String, DocMeta> readMetadata(Path dossierPath) {
        Path metadataFile = dossierPath.resolve("_metadata.json");
        if (!Files.exists(metadataFile)) {
            return Collections.emptyMap();
        }

        Map<String, DocMeta> map = new HashMap<>();
        try (JsonReader reader = Json.createReader(Files.newBufferedReader(metadataFile, StandardCharsets.UTF_8))) {
            JsonArray arr = reader.readArray();
            for (var value : arr) {
                if (!(value instanceof JsonObject)) {
                    continue;
                }
                JsonObject obj = (JsonObject) value;
                String file = obj.getString("file", "");
                if (file.isBlank()) {
                    continue;
                }
                map.put(file.toLowerCase(Locale.ROOT), new DocMeta(
                        obj.getString("nomDocument", ""),
                        obj.getString("copie", ""),
                        obj.getString("nombre", ""),
                        obj.getString("description", "")
                ));
            }
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
        return map;
    }

    public void viewDocument(ScannedDocumentRow doc) {
        streamDocument(doc, false);
    }

    public void downloadDocument(ScannedDocumentRow doc) {
        streamDocument(doc, true);
    }

    private void streamDocument(ScannedDocumentRow doc, boolean download) {
        if (doc == null || doc.getPath() == null || doc.getPath().isBlank()
                || doc.getDocument() == null || doc.getDocument().isBlank()) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Document introuvable.", null));
            return;
        }

        Path filePath = Paths.get(doc.getPath()).resolve(doc.getDocument()).normalize();
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, "Fichier non trouve sur le serveur.", null));
            return;
        }

        FacesContext ctx = FacesContext.getCurrentInstance();
        ExternalContext ext = ctx.getExternalContext();

        try {
            String contentType = Files.probeContentType(filePath);
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            String fileName = filePath.getFileName().toString();
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            String disposition = (download ? "attachment" : "inline") + "; filename*=UTF-8''" + encodedFileName;
            long fileSize = Files.size(filePath);

            ext.responseReset();
            ext.setResponseContentType(contentType);
            if (fileSize <= Integer.MAX_VALUE) {
                ext.setResponseContentLength((int) fileSize);
            } else {
                ext.setResponseHeader("Content-Length", String.valueOf(fileSize));
            }
            ext.setResponseHeader("Content-Disposition", disposition);
            ext.setResponseHeader("X-Content-Type-Options", "nosniff");

            try (OutputStream out = ext.getResponseOutputStream()) {
                Files.copy(filePath, out);
                out.flush();
            }
            ctx.responseComplete();
        } catch (IOException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            "Erreur lecture fichier : " + e.getMessage(), null));
        }
    }

    private String resolveSearchValue() {
        if ("relation".equalsIgnoreCase(searchType)) {
            searchValue = searchRelationValue;
        } else {
            searchValue = searchPinValue;
        }
        return searchValue;
    }

    public List<String> completeRelation(String query) {
        if (!"relation".equalsIgnoreCase(searchType)) {
            return Collections.emptyList();
        }
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        EntityManager em = getEMF().createEntityManager();
        try {
            return em.createQuery(
                            "select distinct d.relation from " + ArchDossier.class.getSimpleName() + " d " +
                                    "where upper(d.relation) like :query " +
                                    "order by d.relation",
                            String.class)
                    .setParameter("query", "%" + query.trim().toUpperCase(Locale.ROOT) + "%")
                    .setMaxResults(20)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    private void clearResult() {
        idDossier = null;
        portefeuille = null;
        pin = null;
        relation = null;
        charge = null;
        typeArchive = null;
        filiale = null;
        etage = null;
        salle = null;
        rayon = null;
        rangee = null;
        boite = null;
        scannedDocuments = Collections.emptyList();
        transmissionHistory = Collections.emptyList();
        resultLoaded = false;
    }

    private String buildDossierName() {
        String relationValue = relation == null ? "" : relation.trim();
        String pinValue = pin == null ? "" : pin.trim();
        String base = "Dossier num " + idDossier + " : " + relationValue + "-" + pinValue;
        return sanitizeFolderName(base);
    }

    private String sanitizeFolderName(String value) {
        if (value == null) {
            return "Dossier";
        }
        return value.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
    }

    private String toStringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Date toDateValue(Object value) {
        if (value instanceof Date) {
            return (Date) value;
        }
        if (value instanceof java.sql.Timestamp) {
            return new Date(((java.sql.Timestamp) value).getTime());
        }
        return null;
    }

    private String normalizeSearchValue(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String toFilialeLabel(String filialeId) {
        if (filialeId == null || filialeId.isBlank()) {
            return filialeId;
        }
        if ("btk-bank".equals(filialeId)) {
            return "BTK Bank";
        }
        if ("btk-finance".equals(filialeId)) {
            return "BTK Finance";
        }
        return filialeId;
    }

    private String toTypeArchiveLabel(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if ("courant".equalsIgnoreCase(value)) {
            return "Courant";
        }
        if ("intermediaire".equalsIgnoreCase(value)) {
            return "Intermediaire";
        }
        if ("finale".equalsIgnoreCase(value)) {
            return "Finale";
        }
        return value;
    }

    private static synchronized EntityManagerFactory getEMF() {
        if (emf == null || !emf.isOpen()) {
            emf = Persistence.createEntityManagerFactory("btk");
        }
        return emf;
    }

    public String getSearchType() {
        return searchType;
    }

    public void setSearchType(String searchType) {
        this.searchType = searchType;
    }

    public String getSearchValue() {
        return searchValue;
    }

    public void setSearchValue(String searchValue) {
        this.searchValue = searchValue;
    }

    public String getSearchPinValue() {
        return searchPinValue;
    }

    public void setSearchPinValue(String searchPinValue) {
        this.searchPinValue = searchPinValue;
    }

    public String getSearchRelationValue() {
        return searchRelationValue;
    }

    public void setSearchRelationValue(String searchRelationValue) {
        this.searchRelationValue = searchRelationValue;
    }

    public String getPortefeuille() {
        return portefeuille;
    }

    public String getPin() {
        return pin;
    }

    public String getRelation() {
        return relation;
    }

    public String getCharge() {
        return charge;
    }

    public String getTypeArchive() {
        return typeArchive;
    }

    public String getFiliale() {
        return filiale;
    }

    public Integer getEtage() {
        return etage;
    }

    public Integer getSalle() {
        return salle;
    }

    public Integer getRayon() {
        return rayon;
    }

    public Integer getRangee() {
        return rangee;
    }

    public Integer getBoite() {
        return boite;
    }

    public boolean isResultLoaded() {
        return resultLoaded;
    }

    public List<ScannedDocumentRow> getScannedDocuments() {
        return scannedDocuments;
    }

    public List<TransmissionHistoryRow> getTransmissionHistory() {
        return transmissionHistory;
    }

    public static class ScannedDocumentRow implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String document;
        private final String nomDocument;
        private final String copie;
        private final String nombre;
        private final String description;
        private final String nomDossier;
        private final String path;
        private final String utilisateur;
        private final Date dateCreation;

        ScannedDocumentRow(String document, String nomDocument, String copie, String nombre, String description,
                           String nomDossier, String path, String utilisateur, Date dateCreation) {
            this.document = document;
            this.nomDocument = nomDocument;
            this.copie = copie;
            this.nombre = nombre;
            this.description = description;
            this.nomDossier = nomDossier;
            this.path = path;
            this.utilisateur = utilisateur;
            this.dateCreation = dateCreation;
        }

        public String getDocument() {
            return document;
        }

        public String getNomDocument() {
            return nomDocument;
        }

        public String getCopie() {
            return copie;
        }

        public String getNombre() {
            return nombre;
        }

        public String getDescription() {
            return description;
        }

        public String getNomDossier() {
            return nomDossier;
        }

        public String getPath() {
            return path;
        }

        public String getUtilisateur() {
            return utilisateur;
        }

        public Date getDateCreation() {
            return dateCreation;
        }
    }

    public static class TransmissionHistoryRow implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Long idDemande;
        private final String emetteur;
        private final String recepteur;
        private final Date dateSortie;
        private final Date dateRestitution;

        TransmissionHistoryRow(Long idDemande, String emetteur, String recepteur, Date dateSortie, Date dateRestitution) {
            this.idDemande = idDemande;
            this.emetteur = emetteur;
            this.recepteur = recepteur;
            this.dateSortie = dateSortie;
            this.dateRestitution = dateRestitution;
        }

        public Long getIdDemande() {
            return idDemande;
        }

        public String getEmetteur() {
            return emetteur;
        }

        public String getRecepteur() {
            return recepteur;
        }

        public Date getDateSortie() {
            return dateSortie;
        }

        public Date getDateRestitution() {
            return dateRestitution;
        }
    }

    private static class DocMeta implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String nomDocument;
        private final String copie;
        private final String nombre;
        private final String description;

        DocMeta(String nomDocument, String copie, String nombre, String description) {
            this.nomDocument = nomDocument == null ? "" : nomDocument;
            this.copie = copie == null ? "" : copie;
            this.nombre = nombre == null ? "" : nombre;
            this.description = description == null ? "" : description;
        }
    }
}
