import React, { useState } from 'react';
import {
  Plus,
  Trash2,
  Layers,
  DoorOpen,
  SquareDashedBottom,
  ChevronDown,
  ChevronUp,
  Sparkles,
  Copy,
  Building,
  HelpCircle
} from 'lucide-react';
import { ConstructionType, Opening, Room, Segment, UnitSystem } from '../types';
import { CONSTRUCTION_TYPES, QUICK_PRESETS } from '../data/materials';

interface MultiRoomManagerProps {
  rooms: Room[];
  unitSystem: UnitSystem;
  onUpdateRooms: (rooms: Room[]) => void;
  selectedRoomId: string;
  onSelectRoom: (id: string) => void;
}

export const MultiRoomManager: React.FC<MultiRoomManagerProps> = ({
  rooms,
  unitSystem,
  onUpdateRooms,
  selectedRoomId,
  onSelectRoom
}) => {
  const isMetric = unitSystem === 'metric';
  const unitSuffix = isMetric ? 'm' : 'ft';
  const areaSuffix = isMetric ? 'm²' : 'sq ft';

  const [expandedRoomId, setExpandedRoomId] = useState<string>(selectedRoomId || (rooms[0]?.id || ''));

  const handleAddRoom = (type: ConstructionType = 'techo_st') => {
    const constType = CONSTRUCTION_TYPES[type];
    const newRoom: Room = {
      id: `room_${Date.now()}`,
      name: `Nueva Zona ${rooms.length + 1} (${constType.name})`,
      type,
      segments: [
        {
          id: `seg_${Date.now()}`,
          name: 'Tramo Principal',
          length: 5.0,
          width: 3.0,
          repetitions: 1,
          openings: []
        }
      ],
      notes: ''
    };

    const updated = [...rooms, newRoom];
    onUpdateRooms(updated);
    setExpandedRoomId(newRoom.id);
    onSelectRoom(newRoom.id);
  };

  const handleAddPreset = (preset: typeof QUICK_PRESETS[0]) => {
    const newRoom: Room = {
      id: `room_${Date.now()}`,
      name: preset.name,
      type: preset.type,
      segments: [
        {
          id: `seg_${Date.now()}`,
          name: 'Área Base',
          length: isMetric ? preset.length : Number((preset.length * 3.28084).toFixed(1)),
          width: isMetric ? preset.width : Number((preset.width * 3.28084).toFixed(1)),
          repetitions: 1,
          openings: preset.openings.map((op) => ({
            ...op,
            id: `op_${Date.now()}_${Math.random().toString(36).substring(7)}`,
            width: isMetric ? op.width : Number((op.width * 3.28084).toFixed(1)),
            height: isMetric ? op.height : Number((op.height * 3.28084).toFixed(1))
          }))
        }
      ],
      notes: `Cargado desde plantilla: ${preset.name}`
    };

    const updated = [...rooms, newRoom];
    onUpdateRooms(updated);
    setExpandedRoomId(newRoom.id);
    onSelectRoom(newRoom.id);
  };

  const handleDeleteRoom = (roomId: string) => {
    if (rooms.length <= 1) {
      alert('Debe mantener al menos una estancia o zona en el proyecto.');
      return;
    }
    const updated = rooms.filter((r) => r.id !== roomId);
    onUpdateRooms(updated);
    if (expandedRoomId === roomId) {
      setExpandedRoomId(updated[0]?.id || '');
      onSelectRoom(updated[0]?.id || '');
    }
  };

  const handleDuplicateRoom = (room: Room) => {
    const duplicated: Room = {
      ...room,
      id: `room_${Date.now()}`,
      name: `${room.name} (Copia)`,
      segments: room.segments.map((s) => ({
        ...s,
        id: `seg_${Date.now()}_${Math.random().toString(36).substring(7)}`,
        openings: s.openings.map((o) => ({
          ...o,
          id: `op_${Date.now()}_${Math.random().toString(36).substring(7)}`
        }))
      }))
    };
    onUpdateRooms([...rooms, duplicated]);
  };

  const handleUpdateRoomName = (roomId: string, name: string) => {
    onUpdateRooms(
      rooms.map((r) => (r.id === roomId ? { ...r, name } : r))
    );
  };

  const handleUpdateRoomType = (roomId: string, type: ConstructionType) => {
    onUpdateRooms(
      rooms.map((r) => (r.id === roomId ? { ...r, type } : r))
    );
  };

  // Segments methods
  const handleAddSegment = (roomId: string) => {
    onUpdateRooms(
      rooms.map((r) => {
        if (r.id !== roomId) return r;
        return {
          ...r,
          segments: [
            ...r.segments,
            {
              id: `seg_${Date.now()}`,
              name: `Tramo ${r.segments.length + 1}`,
              length: 4.0,
              width: 2.5,
              repetitions: 1,
              openings: []
            }
          ]
        };
      })
    );
  };

  const handleUpdateSegment = (
    roomId: string,
    segId: string,
    field: keyof Segment,
    value: any
  ) => {
    onUpdateRooms(
      rooms.map((r) => {
        if (r.id !== roomId) return r;
        return {
          ...r,
          segments: r.segments.map((s) => {
            if (s.id !== segId) return s;
            return { ...s, [field]: value };
          })
        };
      })
    );
  };

  const handleDeleteSegment = (roomId: string, segId: string) => {
    onUpdateRooms(
      rooms.map((r) => {
        if (r.id !== roomId) return r;
        if (r.segments.length <= 1) {
          alert('Debe haber al menos un tramo de medidas por estancia.');
          return r;
        }
        return {
          ...r,
          segments: r.segments.filter((s) => s.id !== segId)
        };
      })
    );
  };

  // Openings (Doors / Windows)
  const handleAddOpening = (roomId: string, segId: string, type: 'door' | 'window') => {
    const isDoor = type === 'door';
    const newOpening: Opening = {
      id: `op_${Date.now()}`,
      type,
      name: isDoor ? 'Puerta Paso' : 'Ventana',
      width: isDoor ? (isMetric ? 0.82 : 2.7) : (isMetric ? 1.20 : 3.9),
      height: isDoor ? (isMetric ? 2.05 : 6.7) : (isMetric ? 1.10 : 3.6),
      count: 1
    };

    onUpdateRooms(
      rooms.map((r) => {
        if (r.id !== roomId) return r;
        return {
          ...r,
          segments: r.segments.map((s) => {
            if (s.id !== segId) return s;
            return {
              ...s,
              openings: [...s.openings, newOpening]
            };
          })
        };
      })
    );
  };

  const handleUpdateOpening = (
    roomId: string,
    segId: string,
    opId: string,
    field: keyof Opening,
    value: any
  ) => {
    onUpdateRooms(
      rooms.map((r) => {
        if (r.id !== roomId) return r;
        return {
          ...r,
          segments: r.segments.map((s) => {
            if (s.id !== segId) return s;
            return {
              ...s,
              openings: s.openings.map((o) => (o.id === opId ? { ...o, [field]: value } : o))
            };
          })
        };
      })
    );
  };

  const handleDeleteOpening = (roomId: string, segId: string, opId: string) => {
    onUpdateRooms(
      rooms.map((r) => {
        if (r.id !== roomId) return r;
        return {
          ...r,
          segments: r.segments.map((s) => {
            if (s.id !== segId) return s;
            return {
              ...s,
              openings: s.openings.filter((o) => o.id !== opId)
            };
          })
        };
      })
    );
  };

  return (
    <div className="space-y-4">
      {/* Quick Presets Bar */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-3.5 shadow-md">
        <div className="flex items-center justify-between mb-2">
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
            <Sparkles className="w-3.5 h-3.5 text-brand-400" />
            Plantillas Rápidas de Construcción (1 Clic)
          </span>
          <span className="text-[11px] text-slate-500">Ahorra tiempo cargando tipologías comunes</span>
        </div>
        <div className="flex flex-wrap gap-2">
          {QUICK_PRESETS.map((preset) => (
            <button
              key={preset.id}
              onClick={() => handleAddPreset(preset)}
              className="text-xs px-3 py-1.5 bg-slate-800 hover:bg-slate-700 hover:border-brand-500/50 border border-slate-700 rounded-lg text-slate-200 transition-all flex items-center gap-2 group"
            >
              <Plus className="w-3.5 h-3.5 text-brand-400 group-hover:rotate-90 transition-transform" />
              <span>{preset.name}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Room Tabs & Accordions */}
      <div className="space-y-3">
        {rooms.map((room, roomIdx) => {
          const isExpanded = expandedRoomId === room.id;
          const isSelected = selectedRoomId === room.id;
          const typeDef = CONSTRUCTION_TYPES[room.type] || CONSTRUCTION_TYPES.techo_st;

          // Compute room area
          let roomGrossArea = 0;
          let roomDeductions = 0;
          room.segments.forEach((seg) => {
            const gross = seg.length * seg.width * (seg.repetitions || 1);
            roomGrossArea += gross;
            seg.openings.forEach((op) => {
              roomDeductions += op.width * op.height * (op.count || 1);
            });
          });
          const roomNetArea = Math.max(0, roomGrossArea - roomDeductions);

          return (
            <div
              key={room.id}
              className={`rounded-2xl transition-all border ${
                isSelected
                  ? 'bg-slate-900/95 border-brand-500/60 shadow-lg shadow-brand-500/5 ring-1 ring-brand-500/30'
                  : 'bg-slate-900/70 border-slate-800 hover:border-slate-700'
              }`}
            >
              {/* Header */}
              <div
                className="p-4 flex flex-wrap items-center justify-between gap-3 cursor-pointer select-none"
                onClick={() => {
                  setExpandedRoomId(isExpanded ? '' : room.id);
                  onSelectRoom(room.id);
                }}
              >
                <div className="flex items-center space-x-3">
                  <div
                    className="w-8 h-8 rounded-lg flex items-center justify-center font-bold text-xs"
                    style={{
                      backgroundColor: `${typeDef.accentColor}25`,
                      color: typeDef.accentColor,
                      border: `1px solid ${typeDef.accentColor}50`
                    }}
                  >
                    #{roomIdx + 1}
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <input
                        type="text"
                        value={room.name}
                        onClick={(e) => e.stopPropagation()}
                        onChange={(e) => handleUpdateRoomName(room.id, e.target.value)}
                        className="bg-transparent font-semibold text-slate-100 hover:bg-slate-800/60 focus:bg-slate-800 px-2 py-0.5 rounded border border-transparent focus:border-brand-500 text-sm focus:outline-none"
                      />
                    </div>
                    <div className="flex items-center gap-3 text-xs text-slate-400 mt-0.5 px-2">
                      <span className="flex items-center gap-1">
                        <span
                          className="w-2 h-2 rounded-full inline-block"
                          style={{ backgroundColor: typeDef.accentColor }}
                        />
                        {typeDef.name}
                      </span>
                      <span>•</span>
                      <span className="font-mono text-brand-400 font-medium">
                        Neto: {roomNetArea.toFixed(2)} {areaSuffix}
                      </span>
                      {roomDeductions > 0 && (
                        <span className="text-slate-500 font-mono">
                          (Deducción: -{roomDeductions.toFixed(2)} {areaSuffix})
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                {/* Right controls */}
                <div className="flex items-center space-x-2" onClick={(e) => e.stopPropagation()}>
                  <button
                    onClick={() => handleDuplicateRoom(room)}
                    className="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition-colors"
                    title="Duplicar esta estancia"
                  >
                    <Copy className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => handleDeleteRoom(room.id)}
                    className="p-1.5 text-rose-400 hover:text-rose-300 rounded-lg hover:bg-rose-500/10 transition-colors"
                    title="Eliminar estancia"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                  <div
                    className="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition-colors cursor-pointer"
                    onClick={() => {
                      setExpandedRoomId(isExpanded ? '' : room.id);
                      onSelectRoom(room.id);
                    }}
                  >
                    {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                  </div>
                </div>
              </div>

              {/* Collapsible Content */}
              {isExpanded && (
                <div className="px-4 pb-4 pt-1 border-t border-slate-800/80 space-y-4">
                  {/* Construction Type Selector */}
                  <div className="bg-slate-950 p-3 rounded-xl border border-slate-800 flex flex-wrap items-center justify-between gap-3">
                    <label className="text-xs font-medium text-slate-300 flex items-center gap-1.5">
                      <Layers className="w-3.5 h-3.5 text-brand-400" />
                      Sistema Constructivo:
                    </label>
                    <select
                      value={room.type}
                      onChange={(e) => handleUpdateRoomType(room.id, e.target.value as ConstructionType)}
                      className="bg-slate-900 border border-slate-700 text-slate-200 text-xs rounded-lg px-3 py-1.5 focus:outline-none focus:border-brand-500"
                    >
                      {Object.values(CONSTRUCTION_TYPES).map((ct) => (
                        <option key={ct.id} value={ct.id}>
                          {ct.name}
                        </option>
                      ))}
                    </select>
                  </div>

                  {/* Segments Table */}
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                        Tramos y Dimensiones
                      </span>
                      <button
                        onClick={() => handleAddSegment(room.id)}
                        className="text-xs text-brand-400 hover:text-brand-300 flex items-center gap-1 font-medium"
                      >
                        <Plus className="w-3.5 h-3.5" />
                        Añadir Tramo
                      </button>
                    </div>

                    <div className="space-y-2.5">
                      {room.segments.map((seg, sIdx) => {
                        const segGross = seg.length * seg.width * (seg.repetitions || 1);
                        return (
                          <div
                            key={seg.id}
                            className="bg-slate-950/80 border border-slate-800 rounded-xl p-3 space-y-3"
                          >
                            <div className="flex flex-wrap items-center justify-between gap-2">
                              <input
                                type="text"
                                value={seg.name}
                                onChange={(e) =>
                                  handleUpdateSegment(room.id, seg.id, 'name', e.target.value)
                                }
                                className="bg-transparent text-xs font-semibold text-slate-300 hover:bg-slate-800/50 px-2 py-1 rounded border border-transparent focus:border-slate-700 focus:outline-none"
                              />

                              <div className="flex items-center gap-2">
                                <span className="text-xs text-slate-400 font-mono">
                                  Subtotal: {segGross.toFixed(2)} {areaSuffix}
                                </span>
                                <button
                                  onClick={() => handleDeleteSegment(room.id, seg.id)}
                                  className="text-slate-500 hover:text-rose-400 p-1 transition-colors"
                                  title="Eliminar tramo"
                                >
                                  <Trash2 className="w-3.5 h-3.5" />
                                </button>
                              </div>
                            </div>

                            {/* Inputs: Length, Width, Repetitions */}
                            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                              <div>
                                <label className="block text-[11px] text-slate-400 mb-1">
                                  Longitud ({unitSuffix})
                                </label>
                                <input
                                  type="number"
                                  step="0.05"
                                  min="0.1"
                                  value={seg.length}
                                  onChange={(e) =>
                                    handleUpdateSegment(
                                      room.id,
                                      seg.id,
                                      'length',
                                      parseFloat(e.target.value) || 0
                                    )
                                  }
                                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-slate-100 font-mono focus:border-brand-500 focus:outline-none"
                                />
                              </div>

                              <div>
                                <label className="block text-[11px] text-slate-400 mb-1">
                                  {typeDef.category === 'ceiling' ? `Ancho (${unitSuffix})` : `Altura (${unitSuffix})`}
                                </label>
                                <input
                                  type="number"
                                  step="0.05"
                                  min="0.1"
                                  value={seg.width}
                                  onChange={(e) =>
                                    handleUpdateSegment(
                                      room.id,
                                      seg.id,
                                      'width',
                                      parseFloat(e.target.value) || 0
                                    )
                                  }
                                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-slate-100 font-mono focus:border-brand-500 focus:outline-none"
                                />
                              </div>

                              <div>
                                <label className="block text-[11px] text-slate-400 mb-1">
                                  Repeticiones / Caras
                                </label>
                                <input
                                  type="number"
                                  step="1"
                                  min="1"
                                  max="20"
                                  value={seg.repetitions}
                                  onChange={(e) =>
                                    handleUpdateSegment(
                                      room.id,
                                      seg.id,
                                      'repetitions',
                                      parseInt(e.target.value) || 1
                                    )
                                  }
                                  className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-slate-100 font-mono focus:border-brand-500 focus:outline-none"
                                />
                              </div>
                            </div>

                            {/* Openings Section (Doors & Windows) */}
                            <div className="pt-2 border-t border-slate-900 space-y-2">
                              <div className="flex items-center justify-between">
                                <span className="text-[11px] font-medium text-slate-400 flex items-center gap-1">
                                  <DoorOpen className="w-3.5 h-3.5 text-brand-400" />
                                  Huecos a descontar (Puertas / Ventanas):
                                </span>
                                <div className="flex items-center space-x-1.5">
                                  <button
                                    onClick={() => handleAddOpening(room.id, seg.id, 'door')}
                                    className="text-[10px] px-2 py-0.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded text-slate-200"
                                  >
                                    + Puerta
                                  </button>
                                  <button
                                    onClick={() => handleAddOpening(room.id, seg.id, 'window')}
                                    className="text-[10px] px-2 py-0.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded text-slate-200"
                                  >
                                    + Ventana
                                  </button>
                                </div>
                              </div>

                              {seg.openings && seg.openings.length > 0 ? (
                                <div className="space-y-1.5">
                                  {seg.openings.map((op) => {
                                    const opArea = op.width * op.height * (op.count || 1);
                                    return (
                                      <div
                                        key={op.id}
                                        className="flex flex-wrap items-center justify-between gap-2 bg-slate-900/60 px-2.5 py-1.5 rounded-lg border border-slate-800 text-xs"
                                      >
                                        <div className="flex items-center space-x-2">
                                          <span className="font-medium text-slate-300">
                                            {op.name}
                                          </span>
                                          <span className="text-slate-500">
                                            ({op.width} × {op.height} {unitSuffix})
                                          </span>
                                        </div>

                                        <div className="flex items-center space-x-3">
                                          <span className="text-rose-400 font-mono text-[11px]">
                                            -{opArea.toFixed(2)} {areaSuffix}
                                          </span>
                                          <button
                                            onClick={() => handleDeleteOpening(room.id, seg.id, op.id)}
                                            className="text-slate-500 hover:text-rose-400"
                                            title="Eliminar abertura"
                                          >
                                            <Trash2 className="w-3 h-3" />
                                          </button>
                                        </div>
                                      </div>
                                    );
                                  })}
                                </div>
                              ) : (
                                <p className="text-[11px] text-slate-500 italic">
                                  No hay puertas ni ventanas deducidas en este tramo.
                                </p>
                              )}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Add New Room Button */}
      <button
        onClick={() => handleAddRoom('techo_st')}
        className="w-full py-3 bg-slate-900 hover:bg-slate-850 border border-dashed border-slate-700 hover:border-brand-500/60 rounded-2xl text-slate-300 hover:text-brand-400 font-medium text-sm transition-all flex items-center justify-center gap-2 shadow-sm"
      >
        <Plus className="w-4 h-4" />
        <span>Añadir Otra Estancia / Zona al Proyecto</span>
      </button>
    </div>
  );
};
