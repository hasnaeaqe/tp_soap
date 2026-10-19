package ma.ensakh.soap;

public class Employe {
    private int id;
    private String nom;
    private String prenom;
    private String email;
    private String departement;

    public Employe(int id, String nom, String prenom, String email, String departement) {
        this.id = id; this.nom = nom; this.prenom = prenom; this.email = email; this.departement = departement;
    }
    // Génère les Getters ici (ou utilise l'IDE pour les générer automatiquement)
    public int getId() { return id; }
    public String getNom() { return nom; }
    public String getPrenom() { return prenom; }
    public String getEmail() { return email; }
    public String getDepartement() { return departement; }
}