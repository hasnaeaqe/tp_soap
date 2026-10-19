package ma.ensakh.soap;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EmployeDAO {
    private static final String URL = "jdbc:postgresql://localhost:5432/entreprise_soap";
    private static final String USER = "tp_app";
    private static final String PASSWORD = "TpApp2024!";

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // READ
    public Employe getEmploye(int id) throws SQLException {
        String sql = "SELECT * FROM employe WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Employe(rs.getInt("id"), rs.getString("nom"), rs.getString("prenom"), rs.getString("email"), rs.getString("departement"));
                }
            }
        }
        return null;
    }

    // CREATE
    public int ajouterEmploye(Employe e) throws SQLException {
        String sql = "INSERT INTO employe (nom, prenom, email, departement) VALUES (?, ?, ?, ?) RETURNING id";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, e.getNom());
            ps.setString(2, e.getPrenom());
            ps.setString(3, e.getEmail());
            ps.setString(4, e.getDepartement());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
            }
        }
        return -1;
    }

    // UPDATE
    public boolean modifierEmploye(Employe e) throws SQLException {
        String sql = "UPDATE employe SET nom=?, prenom=?, email=?, departement=? WHERE id=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, e.getNom());
            ps.setString(2, e.getPrenom());
            ps.setString(3, e.getEmail());
            ps.setString(4, e.getDepartement());
            ps.setInt(5, e.getId());
            return ps.executeUpdate() > 0;
        }
    }

    // DELETE
    public boolean supprimerEmploye(int id) throws SQLException {
        String sql = "DELETE FROM employe WHERE id=?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }
}