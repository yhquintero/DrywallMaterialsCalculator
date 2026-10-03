import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  FolderKanban,
  X,
  Plus,
  Trash2,
  Download,
  Upload,
  Clock,
  ArrowRight,
  FolderOpen,
  ShieldCheck,
  ShieldAlert
} from 'lucide-react';
import {
  SavedProject,
  SavedProjectWithIntegrity,
  loadProjectsWithIntegrity
} from '../lib/storage';
import { formatCurrency } from '../lib/calculator';
import { Dialog } from './ui/Dialog';

interface SavedProjectsModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentProjectId: string;
  onLoadProject: (project: SavedProject) => void;
  onNewProject: () => void;
  onDeleteProject: (id: string) => void;
  onExportProject: (project: SavedProject) => void;
  onImportProjectJson: (project: SavedProject) => void;
}

export const SavedProjectsModal: React.FC<SavedProjectsModalProps> = ({
  isOpen,
  onClose,
  currentProjectId,
  onLoadProject,
  onNewProject,
  onDeleteProject,
  onExportProject,
  onImportProjectJson
}) => {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [projects, setProjects] = useState<SavedProjectWithIntegrity[]>([]);
  const [status, setStatus] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null);

  /**
   * La lista se relee (y verifica su SHA-256) cada vez que se abre el diálogo y
   * después de cada operación, de modo que al eliminar o importar una obra la
   * interfaz se actualiza al instante en lugar de mostrar datos obsoletos.
   */
  const refresh = useCallback(async () => {
    setProjects(await loadProjectsWithIntegrity());
  }, []);

  useEffect(() => {
    if (isOpen) {
      setStatus(null);
      void refresh();
    }
  }, [isOpen, refresh]);

  /** El borrado se delega en el contenedor (dueño del almacenamiento). */
  const handleDelete = (id: string) => {
    onDeleteProject(id);
    void refresh();
  };

  if (!isOpen) return null;

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const parsed = JSON.parse(event.target?.result as string);
        const isValid =
          parsed &&
          typeof parsed === 'object' &&
          parsed.config &&
          Array.isArray(parsed.rooms) &&
          typeof parsed.config.projectName === 'string';
        if (!isValid) {
          setStatus({ kind: 'error', text: 'El archivo JSON no tiene un formato válido de proyecto DrywallPro.' });
          return;
        }
        onImportProjectJson(parsed as SavedProject);
        setStatus({ kind: 'ok', text: `Obra «${parsed.config.projectName}» importada correctamente.` });
        void refresh();
      } catch {
        setStatus({ kind: 'error', text: 'No se pudo leer el archivo JSON: está dañado o no es válido.' });
      }
    };
    reader.readAsText(file);
  };

  return (
    <Dialog
      isOpen={isOpen}
      onClose={onClose}
      label="Gestión de proyectos y obras guardadas"
      backdropClassName="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-in fade-in duration-200"
      panelClassName="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-2xl max-h-[85vh] flex flex-col shadow-2xl overflow-hidden"
    >
        {/* Header */}
        <div className="bg-slate-850 px-6 py-4 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center space-x-2.5">
            <div className="p-2 bg-blue-500/10 rounded-xl text-blue-400">
              <FolderKanban className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-100">
                Gestión de Proyectos & Obras Guardadas
              </h2>
              <p className="text-xs text-slate-400">
                Almacenamiento local automático sin necesidad de conexión externa.
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition-colors"
            aria-label="Cerrar la gestión de proyectos"
          >
            <X className="w-5 h-5" aria-hidden="true" />
          </button>
        </div>

        {/* Toolbar */}
        <div className="p-4 bg-slate-950/70 border-b border-slate-800 flex items-center justify-between gap-2">
          <button
            onClick={() => {
              onNewProject();
              onClose();
            }}
            className="px-3 py-1.5 bg-brand-600 hover:bg-brand-500 text-white rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-all shadow"
          >
            <Plus className="w-4 h-4" />
            <span>Crear Nueva Obra en Blanco</span>
          </button>

          <div className="flex items-center space-x-2">
            <button
              onClick={() => fileInputRef.current?.click()}
              className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 rounded-lg text-xs font-medium flex items-center gap-1.5 transition-all"
            >
              <Upload className="w-3.5 h-3.5" />
              <span>Importar JSON</span>
            </button>
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileUpload}
              accept=".json"
              className="hidden"
            />
          </div>
        </div>

        {status && (
          <p
            role="status"
            aria-live="polite"
            className={`mx-4 mt-3 rounded-lg border px-3 py-2 text-[11px] ${
              status.kind === 'ok'
                ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300'
                : 'border-rose-500/30 bg-rose-500/10 text-rose-300'
            }`}
          >
            {status.text}
          </p>
        )}

        {/* Project List */}
        <div className="p-4 overflow-y-auto space-y-2.5 flex-1 text-xs">
          {projects.length === 0 ? (
            <div className="text-center py-12 text-slate-500 space-y-2">
              <FolderOpen className="w-10 h-10 mx-auto opacity-30" />
              <p>No hay proyectos guardados en el almacenamiento.</p>
            </div>
          ) : (
            projects.map((proj) => {
              const isCurrent = proj.id === currentProjectId;
              return (
                <div
                  key={proj.id}
                  className={`p-3.5 rounded-xl border transition-all flex items-center justify-between gap-3 ${
                    isCurrent
                      ? 'bg-brand-500/10 border-brand-500/50 text-white'
                      : 'bg-slate-950/60 border-slate-800 hover:border-slate-700 text-slate-300'
                  }`}
                >
                  <div className="space-y-1">
                    <div className="flex items-center space-x-2">
                      <span className="font-semibold text-slate-100 text-sm">
                        {proj.config.projectName || 'Sin título'}
                      </span>
                      {isCurrent && (
                        <span className="text-[10px] bg-brand-500/20 text-brand-300 px-2 py-0.5 rounded-full font-medium">
                          Activo
                        </span>
                      )}
                    </div>
                    <div className="text-[11px] text-slate-400 flex items-center gap-3">
                      <span>Cliente: {proj.config.clientName || 'N/A'}</span>
                      <span>•</span>
                      <span>{proj.rooms.length} Zonas</span>
                      <span>•</span>
                      <span className="flex items-center gap-1">
                        <Clock className="w-3 h-3 text-slate-500" />
                        {new Date(proj.updatedAt).toLocaleDateString()}
                      </span>
                      {proj.integrity.status === 'verified' && (
                        <span
                          className="flex items-center gap-1 text-emerald-400"
                          title="El checksum SHA-256 coincide: el proyecto no se modificó fuera de la aplicación."
                        >
                          <ShieldCheck className="w-3 h-3" aria-hidden="true" />
                          Íntegro
                        </span>
                      )}
                      {proj.integrity.status === 'mismatch' && (
                        <span
                          className="flex items-center gap-1 text-amber-400"
                          title="El contenido cambió desde el último guardado (edición externa o restauración parcial)."
                        >
                          <ShieldAlert className="w-3 h-3" aria-hidden="true" />
                          Modificado
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Actions */}
                  <div className="flex items-center space-x-1.5">
                    <button
                      onClick={() => onExportProject(proj)}
                      className="p-1.5 text-slate-400 hover:text-white rounded hover:bg-slate-800 transition-colors"
                      title="Exportar archivo JSON"
                      aria-label={`Exportar el proyecto ${proj.config.projectName || 'sin título'} a JSON`}
                    >
                      <Download className="w-4 h-4" aria-hidden="true" />
                    </button>
                    {!isCurrent && (
                      <button
                        onClick={() => handleDelete(proj.id)}
                        className="p-1.5 text-rose-400 hover:text-rose-300 rounded hover:bg-rose-500/10 transition-colors"
                        title="Eliminar proyecto"
                        aria-label={`Eliminar el proyecto ${proj.config.projectName || 'sin título'}`}
                      >
                        <Trash2 className="w-4 h-4" aria-hidden="true" />
                      </button>
                    )}
                    <button
                      onClick={() => {
                        onLoadProject(proj);
                        onClose();
                      }}
                      className="px-3 py-1 bg-slate-800 hover:bg-brand-600 hover:text-white text-slate-200 rounded-lg text-xs font-medium transition-colors flex items-center gap-1"
                    >
                      <span>Abrir</span>
                      <ArrowRight className="w-3 h-3" />
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>
    </Dialog>
  );
};
