-- Colonnes B1 d'E3 nécessaires aux tests B3 dans PostgreSQL temporaire.
ALTER TABLE staff ADD COLUMN role varchar(20), ADD COLUMN active boolean NOT NULL DEFAULT true;
