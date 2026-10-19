package ma.ensakh.soap;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@WebServlet("/soap")
public class SoapServlet extends HttpServlet {
    private static final String SOAP_NS = "http://www.w3.org/2003/05/soap-envelope";
    private static final String APP_NS = "http://ensakh.ma/soap";
    private final EmployeDAO dao = new EmployeDAO();

    // Identifiants pour l'authentification Basic (Question 11)
    private static final String AUTH_USER = "admin";
    private static final String AUTH_PASS = "admin123";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.getWriter().println("Service SOAP Employes disponible. Envoyez une requete POST avec authentification.");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/soap+xml; charset=UTF-8");

        // --- QUESTION 11 : Authentification HTTP Basic ---
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            demanderAuthentification(resp);
            return;
        }
        try {
            String base64Credentials = authHeader.substring("Basic ".length()).trim();
            byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(credDecoded, StandardCharsets.UTF_8);
            final String[] values = credentials.split(":", 2);
            if (!AUTH_USER.equals(values[0]) || !AUTH_PASS.equals(values[1])) {
                demanderAuthentification(resp);
                return;
            }
        } catch (Exception e) {
            demanderAuthentification(resp);
            return;
        }

        // --- TRAITEMENT SOAP ---
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            Document doc = factory.newDocumentBuilder().parse(req.getInputStream());
            Element envelope = doc.getDocumentElement();

            if (!SOAP_NS.equals(envelope.getNamespaceURI()) || !"Envelope".equals(envelope.getLocalName())) {
                envoyerFault(resp, 400, "Sender", "Envelope SOAP 1.2 invalide.");
                return;
            }

            Element body = premierEnfant(envelope, SOAP_NS, "Body");
            if (body == null) { envoyerFault(resp, 400, "Sender", "Body manquant."); return; }

            Element operation = premierElement(body);
            if (operation == null || !APP_NS.equals(operation.getNamespaceURI())) {
                envoyerFault(resp, 400, "Sender", "Operation SOAP invalide.");
                return;
            }

            String opName = operation.getLocalName();

            // Routage des opérations CRUD
            switch (opName) {
                case "getEmploye":
                    traiterGet(operation, resp); break;
                case "ajouterEmploye":
                    traiterAjout(operation, resp); break;
                case "modifierEmploye":
                    traiterModif(operation, resp); break;
                case "supprimerEmploye":
                    traiterSuppr(operation, resp); break;
                default:
                    envoyerFault(resp, 400, "Sender", "Operation inconnue : " + opName);
            }

        } catch (Exception e) {
            getServletContext().log("Erreur interne", e);
            envoyerFault(resp, 500, "Receiver", "Une erreur interne est survenue.");
        }
    }

    // --- LOGIQUE DES OPERATIONS ---

    private void traiterGet(Element op, HttpServletResponse resp) throws Exception {
        int id = Integer.parseInt(getText(op, "id"));
        Employe e = dao.getEmploye(id);
        if (e == null) { envoyerFault(resp, 400, "Sender", "Employe introuvable."); return; }
        construireReponseEmploye(resp, "getEmployeResponse", e);
    }

    private void traiterAjout(Element op, HttpServletResponse resp) throws Exception {
        Employe e = new Employe(0, getText(op, "nom"), getText(op, "prenom"), getText(op, "email"), getText(op, "departement"));
        int newId = dao.ajouterEmploye(e);
        if (newId == -1) { envoyerFault(resp, 500, "Receiver", "Erreur creation."); return; }
        e = dao.getEmploye(newId); // Récupérer l'objet complet avec l'ID
        construireReponseEmploye(resp, "ajouterEmployeResponse", e);
    }

    private void traiterModif(Element op, HttpServletResponse resp) throws Exception {
        int id = Integer.parseInt(getText(op, "id"));
        if (dao.getEmploye(id) == null) { envoyerFault(resp, 400, "Sender", "Employe introuvable pour modification."); return; }
        Employe e = new Employe(id, getText(op, "nom"), getText(op, "prenom"), getText(op, "email"), getText(op, "departement"));
        dao.modifierEmploye(e);
        construireReponseSimple(resp, "modifierEmployeResponse", "Employe modifie avec succes.");
    }

    private void traiterSuppr(Element op, HttpServletResponse resp) throws Exception {
        int id = Integer.parseInt(getText(op, "id"));
        if (!dao.supprimerEmploye(id)) { envoyerFault(resp, 400, "Sender", "Employe introuvable pour suppression."); return; }
        construireReponseSimple(resp, "supprimerEmployeResponse", "Employe supprime avec succes.");
    }

    // --- OUTILS XML ---

    private String getText(Element parent, String tag) {
        Element el = premierEnfant(parent, APP_NS, tag);
        return el != null ? el.getTextContent().trim() : "";
    }

    private Element premierEnfant(Element parent, String ns, String nom) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el && ns.equals(el.getNamespaceURI()) && nom.equals(el.getLocalName())) return el;
        }
        return null;
    }

    private Element premierElement(Element parent) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el) return el;
        }
        return null;
    }

    private void demanderAuthentification(HttpServletResponse resp) throws IOException {
        resp.setHeader("WWW-Authenticate", "Basic realm=\"SOAP-Employes\"");
        resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentification requise.");
    }

    // --- CONSTRUCTION DES REPONSES ---

    private void construireReponseEmploye(HttpServletResponse resp, String opName, Employe e) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element env = doc.createElementNS(SOAP_NS, "env:Envelope");
        env.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:et", APP_NS);
        doc.appendChild(env);
        Element body = doc.createElementNS(SOAP_NS, "env:Body"); env.appendChild(body);
        Element result = doc.createElementNS(APP_NS, "et:" + opName); body.appendChild(result);
        Element emp = doc.createElementNS(APP_NS, "et:employe"); result.appendChild(emp);
        
        ajouterEl(doc, emp, "id", String.valueOf(e.getId()));
        ajouterEl(doc, emp, "nom", e.getNom());
        ajouterEl(doc, emp, "prenom", e.getPrenom());
        ajouterEl(doc, emp, "email", e.getEmail());
        ajouterEl(doc, emp, "departement", e.getDepartement());
        
        ecrire(resp, doc);
    }

    private void construireReponseSimple(HttpServletResponse resp, String opName, String msg) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element env = doc.createElementNS(SOAP_NS, "env:Envelope");
        env.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:et", APP_NS);
        doc.appendChild(env);
        Element body = doc.createElementNS(SOAP_NS, "env:Body"); env.appendChild(body);
        Element result = doc.createElementNS(APP_NS, "et:" + opName); body.appendChild(result);
        ajouterEl(doc, result, "message", msg);
        ecrire(resp, doc);
    }

    private void ajouterEl(Document doc, Element parent, String nom, String val) {
        Element el = doc.createElementNS(APP_NS, "et:" + nom); el.setTextContent(val); parent.appendChild(el);
    }

    private void envoyerFault(HttpServletResponse resp, int statut, String code, String msg) throws IOException {
        resp.setStatus(statut);
        resp.getWriter().write(String.format(
            "<?xml version=\"1.0\"?><env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\"><env:Body><env:Fault><env:Code><env:Value>env:%s</env:Value></env:Code><env:Reason><env:Text>%s</env:Text></env:Reason></env:Fault></env:Body></env:Envelope>",
            code, msg));
    }

    private void ecrire(HttpServletResponse resp, Document doc) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter w = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(w));
        resp.getWriter().write(w.toString());
    }
}