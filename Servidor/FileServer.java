import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
                String ruta = peticionPartes.length > 1 ? peticionPartes[1] : "";
                mensaje = "";
                // Acciones para cada comando
                switch (comando) {
                    case "descargar":
                        enviarArchivo(ruta, direccion, puertoCliente, socketUDP);
                        break;
                    case "subir":
                        recibirArchivo(socketUDP);
                        break;
                    case "crearFolder":
                        mensaje = crearFolder(ruta);
                        break;
                    case "borrar":
                        mensaje = borrar(ruta);
                        break;
                    case "renombrar":
                        mensaje = renombrar(ruta);
                        break;
                    case "listar":
                        mensaje = listar(ruta);
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

     public static void recibirArchivo(DatagramSocket socketUDP) throws IOException {
        byte[] buffer = new byte[TAMANO_FRAGMENTO];
        DatagramPacket nombrePaquete = new DatagramPacket(buffer, buffer.length);
        socketUDP.receive(nombrePaquete);
        String nombreArchivo = new String(nombrePaquete.getData(), 0, nombrePaquete.getLength()).trim();
        System.out.println(" --- Recibiendo archivo: " + nombreArchivo + " --- ");
    
        FileOutputStream fos = new FileOutputStream(nombreArchivo);
        int expectedSeqNum = 0;
    
        while (true) {
            DatagramPacket fragmentoPaquete = new DatagramPacket(buffer, buffer.length);
            socketUDP.receive(fragmentoPaquete);
            String fragmentoMensaje = new String(fragmentoPaquete.getData(), 0, fragmentoPaquete.getLength());
    
            // Verificar si es el fin de la transmisión
            if (fragmentoMensaje.equals("END")) {
                System.out.println(" --- Transferencia completada --- ");
                break;
            }
    
            // Extraer número de secuencia y datos
            String[] partes = fragmentoMensaje.split(":", 2);
            int seqNum = Integer.parseInt(partes[0]);
            String datos = partes[1];
    
            // Verificar el número de secuencia
            if (seqNum == expectedSeqNum) {
                // Escribir datos en el archivo
                fos.write(datos.getBytes());
                System.out.println("Fragmento " + seqNum + " recibido correctamente.");
    
                // Enviar ACK
                String ackMensaje = "ACK:" + seqNum;
                DatagramPacket ackPaquete = new DatagramPacket(ackMensaje.getBytes(), ackMensaje.length(), fragmentoPaquete.getAddress(), fragmentoPaquete.getPort());
                socketUDP.send(ackPaquete);
    
                // Mover la ventana
                expectedSeqNum++;
            } else {
                // Retransmitir el último ACK
                String ackMensaje = "ACK:" + (expectedSeqNum - 1);
                DatagramPacket ackPaquete = new DatagramPacket(ackMensaje.getBytes(), ackMensaje.length(), fragmentoPaquete.getAddress(), fragmentoPaquete.getPort());
                socketUDP.send(ackPaquete);
                System.out.println("Fragmento fuera de orden. Esperando " + expectedSeqNum);
            }
        }
    
        fos.close();
        System.out.println("Archivo " + nombreArchivo + " recibido exitosamente.");
    }

    public static void enviarArchivo(String ruta, InetAddress direccionServidor, int puertoCliente, DatagramSocket socketCliente) throws IOException {
        File archivo = new File(ruta);
        byte[] buffer = new byte[TAMANO_FRAGMENTO];
        if (!archivo.exists()) {
            System.out.println(" --- Error: No se encontró el archivo: " + ruta + " --- ");
            return;
        }
    
        System.out.println(" --- Archivo: " + ruta + " encontrado. Subiendo --- ");
        // Enviar nombre del archivo al servidor
        String nombreArchivo = archivo.getName();
        DatagramPacket nombrePaquete = new DatagramPacket(nombreArchivo.getBytes(), nombreArchivo.length(), direccionServidor, puertoCliente);
        socketCliente.send(nombrePaquete);
    
        FileInputStream fis = new FileInputStream(archivo);
        int totalFragmentos = (int) Math.ceil((double) archivo.length() / TAMANO_FRAGMENTO);
        int base = 0;
        int nextSeqNum = 0;
    
        while (base < totalFragmentos) {
            // Enviar fragmentos dentro de la ventana
            while (nextSeqNum < base + TAMANO_VENTANA && nextSeqNum < totalFragmentos) {
                int bytesLeidos = fis.read(buffer);
                String fragmento = nextSeqNum + ":" + new String(buffer, 0, bytesLeidos);
                DatagramPacket fragmentoPaquete = new DatagramPacket(fragmento.getBytes(), fragmento.length(), direccionServidor, puertoCliente);
                socketCliente.send(fragmentoPaquete);
                System.out.println("Enviado fragmento " + nextSeqNum);
                nextSeqNum++;
            }
    
            // Esperar ACK
            byte[] ackBuffer = new byte[1024];
            DatagramPacket ackPaquete = new DatagramPacket(ackBuffer, ackBuffer.length);
            try {
                socketCliente.setSoTimeout(1000);
                socketCliente.receive(ackPaquete);
                String ackMensaje = new String(ackPaquete.getData(), 0, ackPaquete.getLength());
                int ackNum = Integer.parseInt(ackMensaje.split(":")[1]);
                System.out.println("ACK recibido para fragmento " + ackNum);
                base = ackNum + 1;
                socketCliente.setSoTimeout(0);
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout alcanzado. Retransmitiendo desde el fragmento " + base);
                nextSeqNum = base;
            }
        }
    
        // Enviar mensaje de fin de archivo
        String finMensaje = "END";
        DatagramPacket finPaquete = new DatagramPacket(finMensaje.getBytes(), finMensaje.length(), direccionServidor, puertoCliente);
        socketCliente.send(finPaquete);
        System.out.println(" --- Archivo enviado exitosamente --- ");
        fis.close();
    }

}