package com.unisen.sgp.model.dto;

import com.unisen.sgp.model.entity.DocumentoOrdenCompra;
import java.time.Instant;

/** Versión documental de una orden (sin su contenido: se descarga como .docx). */
public record DocumentoOrdenDTO(int version, String motivo, Instant generadoEn, UsuarioResumenDTO generadoPor,
                                String huellaSha256) {

    public static DocumentoOrdenDTO from(DocumentoOrdenCompra documento) {
        return new DocumentoOrdenDTO(documento.getVersion(), documento.getMotivo(), documento.getGeneradoEn(),
                UsuarioResumenDTO.from(documento.getGeneradoPor()), documento.getHuellaSha256());
    }
}
