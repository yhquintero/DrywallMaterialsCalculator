import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import { CalculationSummary, ProjectConfig, Room } from '../types';
import { formatCurrency } from './calculator';

export function generateQuotePDF(
  summary: CalculationSummary,
  config: ProjectConfig,
  rooms: Room[]
): void {
  try {
    const doc = new jsPDF({
      orientation: 'portrait',
      unit: 'mm',
      format: 'a4'
    });

    const isMetric = config.unitSystem === 'metric';
    const unitLabel = isMetric ? 'm²' : 'sq ft';
    const primaryColor = [22, 163, 74]; // Emerald green brand color
    const darkColor = [15, 23, 42]; // Slate 900
    const grayColor = [100, 116, 139]; // Slate 500

    // Header Background
    doc.setFillColor(darkColor[0], darkColor[1], darkColor[2]);
    doc.rect(0, 0, 210, 38, 'F');

    // Accent line
    doc.setFillColor(primaryColor[0], primaryColor[1], primaryColor[2]);
    doc.rect(0, 38, 210, 2.5, 'F');

    // Title & Company
    doc.setTextColor(255, 255, 255);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(18);
    doc.text(config.contractorCompany || 'DRYWALL PRO MASTER', 15, 18);

    doc.setFontSize(9);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(203, 213, 225);
    doc.text(`Tel: ${config.contractorPhone || 'N/A'}  •  Email: ${config.contractorEmail || 'N/A'}`, 15, 26);
    doc.text(`Responsable Técnico: ${config.contractorName || 'Contratista Autorizado'}`, 15, 32);

    // Document Type & Number
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(14);
    doc.setTextColor(74, 222, 128); // Brand 400
    doc.text('PRESUPUESTO TÉCNICO', 195, 18, { align: 'right' });

    doc.setFontSize(9);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(226, 232, 240);
    const quoteCode = `COT-${Date.now().toString().slice(-6)}`;
    doc.text(`N° Presupuesto: ${quoteCode}`, 195, 26, { align: 'right' });
    doc.text(`Fecha: ${config.quoteDate}  •  Válido hasta: ${config.validUntil}`, 195, 32, { align: 'right' });

    // Client and Project Information Section
    let currentY = 48;
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(11);
    doc.setTextColor(darkColor[0], darkColor[1], darkColor[2]);
    doc.text('DATOS DEL PROYECTO & CLIENTE', 15, currentY);

    doc.setDrawColor(226, 232, 240);
    doc.line(15, currentY + 2, 195, currentY + 2);

    currentY += 8;
    doc.setFontSize(9);
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(71, 85, 105);
    doc.text('Proyecto / Obra:', 15, currentY);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(15, 23, 42);
    doc.text(config.projectName || 'Sin título', 45, currentY);

    doc.setFont('helvetica', 'bold');
    doc.setTextColor(71, 85, 105);
    doc.text('Cliente:', 115, currentY);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(15, 23, 42);
    doc.text(config.clientName || 'Cliente Particular', 135, currentY);

    currentY += 6;
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(71, 85, 105);
    doc.text('Ubicación:', 15, currentY);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(15, 23, 42);
    doc.text(config.projectAddress || 'N/A', 45, currentY);

    doc.setFont('helvetica', 'bold');
    doc.setTextColor(71, 85, 105);
    doc.text('Contacto:', 115, currentY);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(15, 23, 42);
    doc.text(`${config.clientPhone || ''} ${config.clientEmail ? '(' + config.clientEmail + ')' : ''}`, 135, currentY);

    // Summary Metrics Cards in PDF
    currentY += 10;
    doc.setFillColor(248, 250, 252);
    doc.roundedRect(15, currentY, 56, 16, 2, 2, 'F');
    doc.roundedRect(75, currentY, 56, 16, 2, 2, 'F');
    doc.roundedRect(135, currentY, 60, 16, 2, 2, 'F');

    doc.setFontSize(8);
    doc.setTextColor(grayColor[0], grayColor[1], grayColor[2]);
    doc.text('SUPERFICIE NETA', 18, currentY + 5);
    doc.text('ESTANCIAS / ZONAS', 78, currentY + 5);
    doc.text('FACTOR DESPERDICIO', 138, currentY + 5);

    doc.setFont('helvetica', 'bold');
    doc.setFontSize(11);
    doc.setTextColor(primaryColor[0], primaryColor[1], primaryColor[2]);
    doc.text(`${summary.totalNetArea} ${unitLabel}`, 18, currentY + 12);

    doc.setTextColor(darkColor[0], darkColor[1], darkColor[2]);
    doc.text(`${rooms.length} Zonas registradas`, 78, currentY + 12);
    doc.text(`${config.wastePercentage}% Técnico estándar`, 138, currentY + 12);

    // Material Breakdown Table
    currentY += 22;
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(11);
    doc.text('CÓMPUTO MÉTRICO & MATERIALES REQUERIDOS', 15, currentY);

    const tableRows = summary.requirements.map((req, idx) => [
      (idx + 1).toString(),
      req.name,
      req.commercialFormat,
      req.commercialUnits.toString(),
      formatCurrency(req.unitPrice, config.currency),
      formatCurrency(req.totalPrice, config.currency)
    ]);

    autoTable(doc, {
      startY: currentY + 4,
      head: [['#', 'Descripción del Material', 'Formato Comercial', 'Cant.', 'Precio Unit.', 'Subtotal']],
      body: tableRows,
      theme: 'grid',
      headStyles: {
        fillColor: [15, 23, 42],
        textColor: [255, 255, 255],
        fontSize: 8,
        fontStyle: 'bold'
      },
      styles: {
        fontSize: 8,
        cellPadding: 2.2,
        textColor: [30, 41, 59]
      },
      columnStyles: {
        0: { cellWidth: 8, halign: 'center' },
        1: { cellWidth: 70 },
        2: { cellWidth: 45 },
        3: { cellWidth: 16, halign: 'center' },
        4: { cellWidth: 26, halign: 'right' },
        5: { cellWidth: 25, halign: 'right' }
      },
      margin: { left: 15, right: 15 }
    });

    // Financial Totals Box
    const finalY = (doc as any).lastAutoTable?.finalY || currentY + 60;
    let totalsY = finalY + 8;

    if (totalsY > 230) {
      doc.addPage();
      totalsY = 20;
    }

    const boxWidth = 85;
    const boxX = 195 - boxWidth;

    doc.setFillColor(248, 250, 252);
    doc.rect(boxX, totalsY, boxWidth, 42, 'F');
    doc.setDrawColor(226, 232, 240);
    doc.rect(boxX, totalsY, boxWidth, 42, 'D');

    doc.setFontSize(9);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(71, 85, 105);

    doc.text('Suministro de Materiales:', boxX + 4, totalsY + 7);
    doc.text(formatCurrency(summary.materialsCost, config.currency), 191, totalsY + 7, { align: 'right' });

    doc.text('Mano de Obra Especializada:', boxX + 4, totalsY + 14);
    doc.text(formatCurrency(summary.laborCost, config.currency), 191, totalsY + 14, { align: 'right' });

    doc.text(`Margen Operativo (${config.profitPercentage}%):`, boxX + 4, totalsY + 21);
    doc.text(formatCurrency(summary.profitAmount, config.currency), 191, totalsY + 21, { align: 'right' });

    doc.text(`Impuestos / IVA (${config.taxPercentage}%):`, boxX + 4, totalsY + 28);
    doc.text(formatCurrency(summary.taxAmount, config.currency), 191, totalsY + 28, { align: 'right' });

    // Grand total highlight
    doc.setFillColor(primaryColor[0], primaryColor[1], primaryColor[2]);
    doc.rect(boxX, totalsY + 32, boxWidth, 10, 'F');
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(10);
    doc.setTextColor(255, 255, 255);
    doc.text('TOTAL PRESUPUESTO:', boxX + 4, totalsY + 38.5);
    doc.text(formatCurrency(summary.grandTotal, config.currency), 191, totalsY + 38.5, { align: 'right' });

    // Notes and Conditions
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(9);
    doc.setTextColor(darkColor[0], darkColor[1], darkColor[2]);
    doc.text('NOTAS Y CONDICIONES GENERALES:', 15, totalsY + 6);

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(8);
    doc.setTextColor(100, 116, 139);
    const splitNotes = doc.splitTextToSize(config.notes || 'Sin notas especiales.', boxX - 22);
    doc.text(splitNotes, 15, totalsY + 12);

    // Signatures
    const signY = totalsY + 54;
    if (signY <= 265) {
      doc.setDrawColor(203, 213, 225);
      doc.line(20, signY + 12, 85, signY + 12);
      doc.line(125, signY + 12, 190, signY + 12);

      doc.setFontSize(8);
      doc.text('Firma y Sello del Contratista', 52.5, signY + 17, { align: 'center' });
      doc.text('Aceptación y Firma del Cliente', 157.5, signY + 17, { align: 'center' });
    }

    // Footer
    doc.setFontSize(7);
    doc.setTextColor(148, 163, 184);
    doc.text(
      'Documento emitido con DrywallPro Master • Estándares internacionales de tabiquería y techos suspendidos.',
      105,
      290,
      { align: 'center' }
    );

    // Save PDF
    const filename = `Presupuesto_${config.projectName.replace(/\s+/g, '_')}_${Date.now()}.pdf`;
    doc.save(filename);
  } catch (err) {
    console.error('Error generating PDF:', err);
    alert('Hubo un error al generar el PDF. Abriendo vista de impresión nativa.');
    window.print();
  }
}

export function generatePurchaseOrderPDF(
  summary: CalculationSummary,
  config: ProjectConfig
): void {
  try {
    const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });

    const darkColor = [30, 41, 59];
    const blueColor = [37, 99, 235];

    // Header
    doc.setFillColor(darkColor[0], darkColor[1], darkColor[2]);
    doc.rect(0, 0, 210, 32, 'F');

    doc.setFillColor(blueColor[0], blueColor[1], blueColor[2]);
    doc.rect(0, 32, 210, 2, 'F');

    doc.setFont('helvetica', 'bold');
    doc.setFontSize(16);
    doc.setTextColor(255, 255, 255);
    doc.text('ORDEN DE COMPRA PARA ALMACÉN / PROVEEDOR', 15, 18);

    doc.setFontSize(9);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(203, 213, 225);
    doc.text(`Obra de Destino: ${config.projectName}  •  Entrega en: ${config.projectAddress}`, 15, 26);

    const poCode = `OC-${Date.now().toString().slice(-6)}`;
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(96, 165, 250);
    doc.text(`N° Pedido: ${poCode}`, 195, 20, { align: 'right' });
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(226, 232, 240);
    doc.text(`Fecha: ${new Date().toLocaleDateString()}`, 195, 26, { align: 'right' });

    const tableRows = summary.requirements
      .filter((r) => r.toBuyQuantity > 0 || r.commercialUnits > 0)
      .map((r, i) => [
        (i + 1).toString(),
        r.name,
        r.category.toUpperCase(),
        r.commercialFormat,
        r.availableStock > 0 ? `${r.availableStock}` : '0',
        `${r.toBuyQuantity}`,
        formatCurrency(r.unitPrice, config.currency),
        formatCurrency(r.toBuyQuantity * r.unitPrice, config.currency)
      ]);

    autoTable(doc, {
      startY: 42,
      head: [['#', 'Material Solicitado', 'Rubro', 'Formato Embalaje', 'Stock', 'A Despachar', 'P. Unitario', 'Subtotal Estimado']],
      body: tableRows,
      theme: 'grid',
      headStyles: {
        fillColor: [37, 99, 235],
        textColor: [255, 255, 255],
        fontSize: 8,
        fontStyle: 'bold'
      },
      styles: { fontSize: 8, cellPadding: 2.2 },
      columnStyles: {
        0: { cellWidth: 8, halign: 'center' },
        1: { cellWidth: 55 },
        2: { cellWidth: 22 },
        3: { cellWidth: 35 },
        4: { cellWidth: 14, halign: 'center' },
        5: { cellWidth: 18, halign: 'center', fontStyle: 'bold' },
        6: { cellWidth: 20, halign: 'right' },
        7: { cellWidth: 22, halign: 'right' }
      },
      margin: { left: 15, right: 15 }
    });

    const finalY = (doc as any).lastAutoTable?.finalY || 150;
    const totalOrderCost = summary.requirements.reduce((sum, r) => sum + r.toBuyQuantity * r.unitPrice, 0);

    doc.setFont('helvetica', 'bold');
    doc.setFontSize(10);
    doc.setTextColor(darkColor[0], darkColor[1], darkColor[2]);
    doc.text(`TOTAL ESTIMADO ORDEN DE COMPRA: ${formatCurrency(totalOrderCost, config.currency)}`, 195, finalY + 10, { align: 'right' });

    doc.save(`OrdenCompra_${config.projectName.replace(/\s+/g, '_')}_${Date.now()}.pdf`);
  } catch (err) {
    console.error('Error generating purchase order PDF:', err);
    window.print();
  }
}
