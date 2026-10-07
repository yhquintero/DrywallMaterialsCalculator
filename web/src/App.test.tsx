// @vitest-environment jsdom
import React from 'react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { App } from './App';
import { Root } from './main';

/**
 * Pruebas de humo de la interfaz: montan la aplicación real en un DOM y
 * verifican la estructura accesible y los flujos básicos de diálogo, que es
 * justo lo que la revisión técnica señaló como ausente.
 */
describe('App — estructura accesible y diálogos', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  afterEach(() => {
    cleanup();
    window.localStorage.clear();
  });

  it('muestra la cabecera, las tres pestañas y el salto al contenido', () => {
    render(<App />);

    expect(screen.getByRole('link', { name: /saltar al contenido principal/i })).toBeTruthy();

    const tablist = screen.getByRole('tablist', { name: /secciones de la aplicación/i });
    const tabs = within(tablist).getAllByRole('tab');
    expect(tabs).toHaveLength(3);
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');

    expect(screen.getByRole('tabpanel')).toBeTruthy();
    expect(screen.getByRole('main')).toBeTruthy();
  });

  it('expone los botones de icono con nombre accesible', () => {
    render(<App />);

    expect(screen.getByRole('button', { name: /mis obras y proyectos guardados/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /catálogo de materiales/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /planos cad/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /escanear la estancia/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /precios de distribuidores/i })).toBeTruthy();
  });

  it('abre la configuración como diálogo modal con nombre y la cierra con Escape', async () => {
    render(<App />);

    // El botón «engranaje» de la cabecera (el logotipo tiene otro nombre).
    const settings = screen.getByRole('button', { name: 'Configuración del proyecto' });
    fireEvent.click(settings);

    const dialog = await screen.findByRole('dialog', {
      name: 'Configuración del proyecto y parámetros técnicos'
    });
    expect(dialog.getAttribute('aria-modal')).toBe('true');

    // Escape cierra el diálogo (comportamiento del contenedor accesible).
    fireEvent.keyDown(dialog, { key: 'Escape' });
    expect(
      screen.queryByRole('dialog', { name: 'Configuración del proyecto y parámetros técnicos' })
    ).toBeNull();
  });

  it('cambia de pestaña actualizando aria-selected', () => {
    render(<App />);

    const budgetTab = screen.getByRole('tab', { name: /presupuesto/i });
    fireEvent.click(budgetTab);

    expect(budgetTab.getAttribute('aria-selected')).toBe('true');
    expect(screen.getByRole('tabpanel').getAttribute('id')).toBe('panel-budget');
  });

  it('expone acceso directo a la creación de licencias con Keygen en cabecera y pie', () => {
    render(<App />);

    const headerKeygenLink = screen.getByRole('link', {
      name: /abrir la consola profesional de licencias y keygen/i,
    });
    expect(headerKeygenLink.getAttribute('href')).toBe('/admin/licenses?emitir=1');

    const footerKeygenLink = screen.getByRole('link', {
      name: /crear licencia \(keygen\)/i,
    });
    expect(footerKeygenLink.getAttribute('href')).toBe('/admin/licenses?emitir=1');
  });

  it('enruta /admin y /admin/licenses?emitir=1 a la consola de licencias (sin pantalla en blanco)', async () => {
    render(
      <MemoryRouter initialEntries={['/admin/licenses?emitir=1']}>
        <Root />
      </MemoryRouter>
    );

    // Sin sesión iniciada, RequireAuth redirige a /admin/login y muestra el acceso al Keygen.
    const heading = await screen.findByRole('heading', {
      name: /consola de licencias & keygen/i,
    });
    expect(heading).toBeTruthy();
    expect(screen.getByPlaceholderText('admin')).toBeTruthy();
  });
});
