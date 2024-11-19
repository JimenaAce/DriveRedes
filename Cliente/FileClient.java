import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.Scanner;

public class FileClient {
    private static final String IP_SERVIDOR = "127.0.0.1";
    //private static final String IP_SERVIDOR = "26.183.224.23";
    private static final int PUERTO_SERVIDOR = 8000;
    private static final int TAMANO_FRAGMENTO = 1024;
    private static final int TAMANO_VENTANA = 5;

    public static void main(String[] args) {
        try {
            // Se crea el socket del cliente (sin puerto)
            InetAddress direccionServidor = InetAddress.getByName(IP_SERVIDOR); // (localhost)
            DatagramSocket socketCliente = new DatagramSocket();
            System.out.println("Cliente de archivos iniciado.");

            Scanner input = new Scanner(System.in);
            String mensaje = "";

            //for (;;) {
                System.out.println("Escriba la acción a realizar:");
                System.out.println("  - descargar:<ruta_del_archivo>");
                System.out.println("  - subir:<ruta_del_archivo>");
                System.out.println("  - crearFolder:<ruta_de_la_carpeta>");
                System.out.println("  - borrar:<ruta_del_archivo_o_carpeta>");
                System.out.println("  - renombrar:<ruta_vieja>,<ruta_nueva>");
                System.out.println("  - listar:<ruta_de_la_carpeta>");
                System.out.println("  - salir");
                mensaje = "";
                mensaje = input.nextLine(); // Lee la acción a realizar

                if (mensaje.equalsIgnoreCase("salir")) {
                    System.out.println("Saliendo del cliente...");
                    //break;
                }
                enviarSolicitud(socketCliente, direccionServidor, mensaje);
                System.out.println("Saliendo del cliente...");
            //}
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void enviarSolicitud(DatagramSocket socketCliente, InetAddress direccionServidor, String mensaje)
            throws IOException {
        byte[] buffer = new byte[1024];
        buffer = mensaje.getBytes();
        DatagramPacket solicitud = new DatagramPacket(buffer, buffer.length, direccionServidor, PUERTO_SERVIDOR);
        socketCliente.send(solicitud);

        String[] peticionPartes = mensaje.split(":", 2);
        String comando = peticionPartes[0];
        String ruta = peticionPartes.length > 1 ? peticionPartes[1] : "";
        switch (comando) {
            case "descargar":
                recibirArchivo(socketCliente);
                break;
            case "subir":
                enviarArchivo(ruta, direccionServidor, socketCliente);
                break;
            default:
                recibirRespuesta(socketCliente);
                break;
        }
    }
    public static void enviarArchivo(String ruta, InetAddress direccionServidor, DatagramSocket socketCliente) throws IOException {
        File archivo = new File(ruta);
        byte[] buffer = new byte[TAMANO_FRAGMENTO];
        if (!archivo.exists()) {
            System.out.println(" --- Error: No se encontró el archivo: " + ruta + " --- ");
            return;
        }
    
        System.out.println(" --- Archivo: " + ruta + " encontrado. Subiendo --- ");
        // Enviar nombre del archivo al servidor
        String nombreArchivo = archivo.getName();
        DatagramPacket nombrePaquete = new DatagramPacket(nombreArchivo.getBytes(), nombreArchivo.length(), direccionServidor, PUERTO_SERVIDOR);
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
                DatagramPacket fragmentoPaquete = new DatagramPacket(fragmento.getBytes(), fragmento.length(), direccionServidor, PUERTO_SERVIDOR);
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
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout alcanzado. Retransmitiendo desde el fragmento " + base);
                nextSeqNum = base;
            }
        }
    
        // Enviar mensaje de fin de archivo
        String finMensaje = "END";
        DatagramPacket finPaquete = new DatagramPacket(finMensaje.getBytes(), finMensaje.length(), direccionServidor, PUERTO_SERVIDOR);
        socketCliente.send(finPaquete);
        System.out.println(" --- Archivo enviado exitosamente --- ");
        fis.close();
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

    // Recibe respuesta del servidor
    public static void recibirRespuesta(DatagramSocket socketCliente) throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket peticion = new DatagramPacket(buffer, buffer.length);
        socketCliente.receive(peticion);
        String mensaje = new String(peticion.getData());
        System.out.println(mensaje);
    }
}