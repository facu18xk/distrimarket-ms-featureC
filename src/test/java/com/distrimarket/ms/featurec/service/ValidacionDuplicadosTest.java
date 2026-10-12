package com.distrimarket.ms.featurec.service;

import com.distrimarket.commons.entity.Cliente;
import com.distrimarket.commons.entity.Persona;
import com.distrimarket.commons.entity.Timbrado;
import com.distrimarket.commons.enums.TipoPersona;
import com.distrimarket.ms.featurec.exception.ConflictException;
import com.distrimarket.ms.featurec.mapper.ClienteMapper;
import com.distrimarket.ms.featurec.mapper.TimbradoMapper;
import com.distrimarket.ms.featurec.repository.ClienteRepository;
import com.distrimarket.ms.featurec.repository.PersonaRepository;
import com.distrimarket.ms.featurec.repository.TimbradoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** CI, RUC y número de timbrado son únicos: se rechazan con 409 y un mensaje claro antes de guardar. */
@ExtendWith(MockitoExtension.class)
class ValidacionDuplicadosTest {

    @Mock private ClienteRepository clienteRepository;
    @Mock private ClienteMapper clienteMapper;
    @Mock private PersonaRepository personaRepository;
    @Mock private TimbradoRepository timbradoRepository;
    @Mock private TimbradoMapper timbradoMapper;

    private ClienteService clienteService;
    private TimbradoService timbradoService;

    @BeforeEach
    void setUp() {
        clienteService = new ClienteService(clienteRepository, clienteMapper, personaRepository);
        timbradoService = new TimbradoService(timbradoRepository, timbradoMapper);
        lenient().when(personaRepository.save(any(Persona.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(timbradoRepository.save(any(Timbrado.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void crearClienteConCiYaRegistradoEsConflicto() {
        when(personaRepository.existsByCi("4123456")).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class,
                () -> clienteService.create(cliente(null, persona(null, "4123456", null))));

        assertEquals("Ya existe una persona con el CI 4123456.", error.getMessage());
        verify(personaRepository, never()).save(any());
        verify(clienteRepository, never()).save(any());
    }

    @Test
    void crearClienteConRucYaRegistradoEsConflicto() {
        when(personaRepository.existsByRuc("80012345-6")).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class,
                () -> clienteService.create(cliente(null, persona(null, "5000001", "80012345-6"))));

        assertEquals("Ya existe una persona con el RUC 80012345-6.", error.getMessage());
        verify(personaRepository, never()).save(any());
    }

    @Test
    void crearClienteConDocumentosLibresSeGuardaActivo() {
        Cliente creado = clienteService.create(cliente(null, persona(null, "5000001", null)));

        assertTrue(creado.getEstado());
        verify(personaRepository).save(any(Persona.class));
        verify(clienteRepository).save(creado);
    }

    @Test
    void actualizarClienteConservandoSuPropioCiNoEsConflicto() {
        Cliente existente = cliente(7L, persona(70L, "4123456", null));
        when(clienteRepository.findById(7L)).thenReturn(Optional.of(existente));
        when(personaRepository.existsByCiAndIdNot("4123456", 70L)).thenReturn(false);

        clienteService.update(7L, cliente(null, persona(null, "4123456", null)));

        verify(personaRepository).existsByCiAndIdNot("4123456", 70L);
        verify(personaRepository, never()).existsByCi(any());
        verify(clienteRepository).save(existente);
    }

    @Test
    void actualizarClienteConElCiDeOtraPersonaEsConflictoYNoModificaNada() {
        Cliente existente = cliente(7L, persona(70L, "4123456", null));
        when(clienteRepository.findById(7L)).thenReturn(Optional.of(existente));
        when(personaRepository.existsByCiAndIdNot("9999999", 70L)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> clienteService.update(7L, cliente(null, persona(null, "9999999", null))));

        assertEquals("4123456", existente.getPersona().getCi());
        verify(personaRepository, never()).save(any());
    }

    @Test
    void actualizacionParcialConElRucDeOtraPersonaEsConflictoYNoModificaNada() {
        Cliente existente = cliente(7L, persona(70L, "4123456", null));
        when(clienteRepository.findById(7L)).thenReturn(Optional.of(existente));
        when(personaRepository.existsByRucAndIdNot("80012345-6", 70L)).thenReturn(true);
        Persona cambios = new Persona();
        cambios.setRuc("80012345-6");
        cambios.setTelefono("0981 000000");

        assertThrows(ConflictException.class, () -> clienteService.actualizarParcialmente(7L, cambios));

        assertEquals(null, existente.getPersona().getRuc());
        assertEquals(null, existente.getPersona().getTelefono());
        verify(personaRepository, never()).save(any());
    }

    @Test
    void crearTimbradoConNumeroYaRegistradoEsConflicto() {
        when(timbradoRepository.existsByNumeroTimbrado("16543210")).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class,
                () -> timbradoService.create(timbrado(null, " 16543210 ")));

        assertEquals("Ya existe un timbrado con ese número.", error.getMessage());
        verify(timbradoRepository, never()).save(any());
    }

    @Test
    void crearTimbradoGuardaElNumeroSinEspaciosYActivo() {
        Timbrado creado = timbradoService.create(timbrado(null, " 16543299 "));

        assertEquals("16543299", creado.getNumeroTimbrado());
        assertTrue(creado.getActivo());
    }

    @Test
    void actualizarTimbradoConservandoSuNumeroNoEsConflicto() {
        Timbrado existente = timbrado(5L, "16543210");
        when(timbradoRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(timbradoRepository.existsByNumeroTimbradoAndIdNot("16543210", 5L)).thenReturn(false);

        timbradoService.update(5L, timbrado(null, "16543210"));

        verify(timbradoRepository).save(existente);
    }

    @Test
    void actualizarTimbradoAlNumeroDeOtroEsConflicto() {
        Timbrado existente = timbrado(5L, "16543210");
        when(timbradoRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(timbradoRepository.existsByNumeroTimbradoAndIdNot("16543212", 5L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> timbradoService.update(5L, timbrado(null, "16543212")));

        verify(timbradoRepository, never()).save(any());
    }

    private static Cliente cliente(Long id, Persona persona) {
        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setPersona(persona);
        cliente.setEstado(true);
        return cliente;
    }

    private static Persona persona(Long id, String ci, String ruc) {
        Persona persona = new Persona();
        persona.setId(id);
        persona.setTipoPersona(TipoPersona.FISICA);
        persona.setNombreCompleto("Persona de prueba");
        persona.setCi(ci);
        persona.setRuc(ruc);
        return persona;
    }

    private static Timbrado timbrado(Long id, String numero) {
        Timbrado timbrado = new Timbrado();
        timbrado.setId(id);
        timbrado.setNumeroTimbrado(numero);
        timbrado.setFechaInicio(LocalDate.of(2026, 1, 1));
        timbrado.setFechaVencimiento(LocalDate.of(2026, 12, 31));
        timbrado.setActivo(true);
        return timbrado;
    }
}
