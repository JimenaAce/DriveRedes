import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class FileServer {
    private static final int PUERTO = 8000;
    private static final int TAMANO_FRAGMENTO = 1024;
    private static final int TAMANO_VENTANA = 5;

    public static void main(String[] args) {
        try {
            DatagramSocket socketUDP = new DatagramSocket(PUERTO);
            System.out.println("Servidor de archivos iniciado en el puerto " + PUERTO);
            for (;;) {
                // Espera solicitud del cliente
                byte[] buffer = new byte[1024];
                DatagramPacket peticion = new DatagramPacket(buffer, buffer.length);
                socketUDP.receive(peticion);
                // Obtiene mensaje y encuentra dirección y puerto del cliente
                String mensaje = new String(peticion.getData()).trim(); // Elimina espacios
                InetAddress direccion = peticion.getAddress();
                int puertoCliente = peticion.getPort();
                // Imprime el mensaje y separa comando y argumento
                System.out.println(
                        "Se recibió petición: " + mensaje + " desde: " + direccion + " en el puerto " + puertoCliente);
                String[] peticionPartes = mensaje.split(":", 2);
                String comando = peticionPartes[0];
                String argumento = peticionPartes.length > 1 ? peticionPartes[1] : "";
                mensaje = "";
                // Acciones para cada comando
                switch (comando) {
                    case "descargar":
                        enviarArchivo(argumento, direccion, puertoCliente, socketUDP);
                        continue;
                    case "subir":
                        // recibirArchivo(argumento, direccion, puertoCliente, socketUDP);
                        // mensaje = "Archivo recibido: " + argumento;
                        break;
                    case "crearFolder":
                        mensaje = crearFolder(argumento);
                        break;
                    case "borrar":
                        mensaje = borrar(argumento);
                        break;
                    case "renombrar":
                        mensaje = renombrar(argumento);
                        break;
                    case "listar":
                        mensaje = listar(argumento);
                        break;
                    default:
                        mensaje = "Comando no reconocido: " + comando;
                        break;
                }
                // Se envía respuesta al cliente
                buffer = new byte[1024];
                buffer = mensaje.getBytes();
                System.out.println("Enviando información al cliente...");
                DatagramPacket respuesta = new DatagramPacket(buffer, buffer.length, direccion, puertoCliente);
                socketUDP.send(respuesta);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void enviarArchivo(String ruta, InetAddress direccion, int puertoCliente, DatagramSocket socketUDP)
            throws IOException {
        Path path = Paths.get(ruta);
        String mensaje = "";
        if (!Files.exists(path)) {
            mensaje = "Error";
            socketUDP.send(new DatagramPacket(mensaje.getBytes(), mensaje.getBytes().length, direccion, puertoCliente));
            return;
        } else {
            mensaje = "Servidor enviando archivo...";
            socketUDP.send(new DatagramPacket(mensaje.getBytes(), mensaje.getBytes().length, direccion, puertoCliente));
        }
    }

    // Función para crear folder
    private static String crearFolder(String rutaFolder) {
        try {
            Files.createDirectories(Paths.get(rutaFolder));
            return " --- Carpeta creada en: " + rutaFolder + " --- ";
        } catch (IOException e) {
            return " --- Error al crear carpeta: " + e.getMessage() + " --- ";
        }
    }

    // Función para borrar archivo o carpeta
    private static String borrar(String ruta) {
        try {
            Files.delete(Paths.get(ruta));
            return "--- Archivo o carpeta eliminada: " + ruta + " --- ";
        } catch (IOException e) {
            return " --- Error al eliminar archivo o carpeta: " + e.getMessage() + " --- ";
        }
    }

    // Función para renombrar un archivo o carpeta
    private static String renombrar(String rutas) {
        String[] arregloRutas = rutas.split(",");
        if (arregloRutas.length != 2) {
            return "Error: se requieren las rutas 'vieja' y 'nueva' separadas por coma.";
        }
        Path rutaVieja = Paths.get(arregloRutas[0].trim());
        Path rutaNueva = Paths.get(arregloRutas[1].trim());
        try {
            Files.move(rutaVieja, rutaNueva);
            return "Renombrado de " + rutaVieja + " a " + rutaNueva + " exitoso.";
        } catch (IOException e) {
            return "Error al renombrar: " + e.getMessage();
        }
    }

    // Función para listar archivos de ruta
    private static String listar(String rutaFolder) {
        try {
            Path ruta = Paths.get(rutaFolder);
            if (!Files.isDirectory(ruta)) {
                return "La ruta no es una carpeta: " + rutaFolder;
            }
            StringBuilder fileList = new StringBuilder();
            DirectoryStream<Path> stream = Files.newDirectoryStream(ruta);
            for (Path file : stream) {
                fileList.append(file.getFileName().toString()).append("\n");
            }
            stream.close();
            return fileList.length() > 0 ? fileList.toString() : "La carpeta está vacía.";
        } catch (IOException e) {
            return "Error al listar archivos: " + e.getMessage();
        }
    }
}