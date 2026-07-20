ALTER TABLE position_theses DROP CONSTRAINT ck_position_theses_status;

UPDATE position_theses
SET status = 'CRITERIOS_EM_ATENCAO'
WHERE status = 'PREMISSAS_ALTERADAS';

ALTER TABLE position_theses
    ADD CONSTRAINT ck_position_theses_status
        CHECK (status IN (
            'DADOS_INSUFICIENTES',
            'EM_ESTUDO',
            'CRITERIOS_ATENDIDOS',
            'CRITERIOS_PARCIALMENTE_ATENDIDOS',
            'CRITERIOS_EM_ATENCAO',
            'DADOS_DESATUALIZADOS'
        ));
