import java.io.*;
import java.net.*;
import java.util.Scanner;

public class FileClient {
    private static final String IP_SERVIDOR = "127.0.0.1";
    private static final int PUERTO_SERVIDOR = 8000;
    private static final int TAMANO_FRAGMENTO = 1024;

    public static void main(String[] args) {
        try {
            // Se crea el socket del cliente (sin puerto)
            InetAddress direccionServidor = InetAddress.getByName(IP_SERVIDOR); // (localhost)
            DatagramSocket socketCliente = new DatagramSocket();
            System.out.println("Cliente de archivos iniciado.");

            Scanner input = new Scanner(System.in);

            for (;;) {
                System.out.println("Escriba la acción a realizar:");
                System.out.println("  - descargar:<ruta_del_archivo>");
                System.out.println("  - subir:<ruta_del_archivo>");
                System.out.println("  - crearFolder:<ruta_de_la_carpeta>");
                System.out.println("  - borrar:<ruta_del_archivo_o_carpeta>");
                System.out.println("  - renombrar:<ruta_vieja>,<ruta_nueva>");
                System.out.println("  - listar:<ruta_de_la_carpeta>");
                System.out.println("  - salir");
                String mensaje = input.nextLine(); // Lee la acción a realizar

                if (mensaje.equalsIgnoreCase("salir")) {
                    System.out.println("Saliendo del cliente...");
                    break;
                }
                enviarSolicitud(socketCliente, direccionServidor, mensaje);
            }
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

        String[] command = mensaje.split(":", 2);
        switch (command[0]) {
            case "descargar":
                recibirArchivo(command[1], socketCliente);
                break;
            case "subir":
                // enviarArchivo(command[1], direccionServidor, socketCliente);
                break;
            default:
                recibirRespuesta(socketCliente);
                break;
        }
    }
    // Recibe un archivo fragmentado del servidor
    public static void recibirArchivo(String ruta, DatagramSocket socketCliente) throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket peticion = new DatagramPacket(buffer, buffer.length);
        socketCliente.receive(peticion);
        String mensaje = new String(peticion.getData());
        System.out.println(mensaje);

        if (mensaje.charAt(0) == 'E') {
            System.out.println(" --- Error: Archivo no encontrado --- a");
        } else {
            /*
             * AQUÍ IMPLEMENTAR LÓGICA PARA RECIBIR ARCHIVO
             */
            System.out.println(" --- Archivo existe --- ");
        }
    }
    // Envia un archivo fragmentado al servidor
    public static void enviarArchivo(String ruta, InetAddress direccionServidor, DatagramSocket socketCliente){
       
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