#!/usr/bin/env bash

set -uo pipefail

API_URL="${API_URL:-http://localhost:8080/api/v1/admin/assets}"
TOKEN="${TOKEN:-}"
REQUEST_DELAY="${REQUEST_DELAY:-0.10}"
DRY_RUN="${DRY_RUN:-false}"

SUCCESS_COUNT=0
SKIPPED_COUNT=0
ERROR_COUNT=0

if ! command -v curl >/dev/null 2>&1; then
  echo "Erro: curl não está instalado." >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "Erro: jq não está instalado." >&2
  exit 1
fi

if [[ "$DRY_RUN" != "true" && -z "$TOKEN" ]]; then
  echo "Erro: informe o JWT na variável TOKEN." >&2
  echo "Exemplo: TOKEN='seu-token' ./cadastrar-60-principais-empresas-b3.sh" >&2
  exit 1
fi

create_asset() {
  local symbol="$1"
  local name="$2"
  local sector="$3"
  local industry="$4"

  local monitoring_reason
  monitoring_reason="Ativo ${symbol} da empresa ${name}, selecionado para estudo e acompanhamento de position trade na B3. Monitorar tendência primária e secundária, força relativa, volume, liquidez, suportes e resistências, volatilidade, resultados, catalisadores, valuation, endividamento, riscos setoriais e eventos corporativos."

  local payload
  payload="$(
    jq -n \
      --arg symbol "$symbol" \
      --arg name "$name" \
      --arg sector "$sector" \
      --arg industry "$industry" \
      --arg reason "$monitoring_reason" \
      '{
        symbol: $symbol,
        name: $name,
        sector: $sector,
        industry: $industry,
        market: "B3",
        assetType: "STOCK",
        active: true,
        monitoringReason: $reason
      }'
  )"

  if [[ "$DRY_RUN" == "true" ]]; then
    echo "$payload"
    ((SUCCESS_COUNT += 1))
    return
  fi

  local response_file
  response_file="$(mktemp)"

  local http_status
  http_status="$(
    curl --silent --show-error --location \
      --connect-timeout 15 \
      --max-time 60 \
      --output "$response_file" \
      --write-out '%{http_code}' \
      "$API_URL" \
      --header 'Content-Type: application/json' \
      --header "Authorization: Bearer ${TOKEN}" \
      --data "$payload" || printf '000'
  )"

  case "$http_status" in
    2??)
      echo "OK     ${symbol} - ${name}"
      ((SUCCESS_COUNT += 1))
      ;;
    409)
      echo "PULADO ${symbol} - ativo já cadastrado"
      ((SKIPPED_COUNT += 1))
      ;;
    *)
      echo "ERRO   ${symbol} - HTTP ${http_status}" >&2
      if [[ -s "$response_file" ]]; then
        cat "$response_file" >&2
        echo >&2
      fi
      ((ERROR_COUNT += 1))
      ;;
  esac

  rm -f "$response_file"
  sleep "$REQUEST_DELAY"
}

create_asset "VALE3" "Vale ON" "Materiais Básicos" "Mineração"
create_asset "ITUB4" "Itaú Unibanco PN" "Financeiro" "Bancos"
create_asset "PETR4" "Petrobras PN" "Petróleo, Gás e Biocombustíveis" "Exploração, Refino e Distribuição"
create_asset "AXIA3" "Axia Energia ON" "Utilidade Pública" "Energia Elétrica"
create_asset "BBAS3" "Banco do Brasil ON" "Financeiro" "Bancos"
create_asset "BBDC4" "Bradesco PN" "Financeiro" "Bancos"
create_asset "ABEV3" "Ambev ON" "Consumo não Cíclico" "Bebidas"
create_asset "WEGE3" "WEG ON" "Bens Industriais" "Máquinas e Equipamentos"
create_asset "B3SA3" "B3 ON" "Financeiro" "Infraestrutura de Mercado Financeiro"
create_asset "ITSA4" "Itaúsa PN" "Financeiro" "Holdings Diversificadas"
create_asset "BPAC11" "BTG Pactual Units" "Financeiro" "Bancos de Investimento"
create_asset "SUZB3" "Suzano ON" "Materiais Básicos" "Papel e Celulose"
create_asset "JBSS3" "JBS ON" "Consumo não Cíclico" "Alimentos Processados"
create_asset "RENT3" "Localiza ON" "Consumo Cíclico" "Locação de Veículos"
create_asset "PRIO3" "PRIO ON" "Petróleo, Gás e Biocombustíveis" "Exploração e Produção"
create_asset "RDOR3" "Rede D'Or ON" "Saúde" "Serviços Médico-Hospitalares"
create_asset "BBSE3" "BB Seguridade ON" "Financeiro" "Seguros"
create_asset "GGBR4" "Gerdau PN" "Materiais Básicos" "Siderurgia"
create_asset "EMBR3" "Embraer ON" "Bens Industriais" "Material de Transporte"
create_asset "EQTL3" "Equatorial Energia ON" "Utilidade Pública" "Energia Elétrica"
create_asset "RADL3" "RD Saúde ON" "Consumo não Cíclico" "Comércio de Medicamentos"
create_asset "VIVT3" "Telefônica Brasil ON" "Comunicações" "Telecomunicações"
create_asset "SANB11" "Santander Brasil Units" "Financeiro" "Bancos"
create_asset "CPLE6" "Copel PNB" "Utilidade Pública" "Energia Elétrica"
create_asset "CMIG4" "Cemig PN" "Utilidade Pública" "Energia Elétrica"
create_asset "CPFE3" "CPFL Energia ON" "Utilidade Pública" "Energia Elétrica"
create_asset "ENGI11" "Energisa Units" "Utilidade Pública" "Energia Elétrica"
create_asset "SBSP3" "Sabesp ON" "Utilidade Pública" "Água e Saneamento"
create_asset "TOTS3" "TOTVS ON" "Tecnologia da Informação" "Software e Serviços"
create_asset "LREN3" "Lojas Renner ON" "Consumo Cíclico" "Varejo de Vestuário"
create_asset "VBBR3" "Vibra Energia ON" "Petróleo, Gás e Biocombustíveis" "Distribuição de Combustíveis"
create_asset "RAIL3" "Rumo ON" "Bens Industriais" "Transporte Ferroviário"
create_asset "CSAN3" "Cosan ON" "Petróleo, Gás e Biocombustíveis" "Holding de Energia e Infraestrutura"
create_asset "UGPA3" "Ultrapar ON" "Petróleo, Gás e Biocombustíveis" "Distribuição de Combustíveis e Infraestrutura"
create_asset "HAPV3" "Hapvida ON" "Saúde" "Planos de Saúde"
create_asset "MOTV3" "Motiva ON" "Bens Industriais" "Concessões e Infraestrutura"
create_asset "KLBN11" "Klabin Units" "Materiais Básicos" "Papel e Celulose"
create_asset "BRFS3" "BRF ON" "Consumo não Cíclico" "Alimentos Processados"
create_asset "TIMS3" "TIM Brasil ON" "Comunicações" "Telecomunicações"
create_asset "ENEV3" "Eneva ON" "Utilidade Pública" "Energia Elétrica"
create_asset "AURE3" "Auren Energia ON" "Utilidade Pública" "Energia Elétrica"
create_asset "BRAV3" "Brava Energia ON" "Petróleo, Gás e Biocombustíveis" "Exploração e Produção"
create_asset "ASAI3" "Assaí ON" "Consumo não Cíclico" "Atacarejo"
create_asset "PSSA3" "Porto Seguro ON" "Financeiro" "Seguros"
create_asset "MULT3" "Multiplan ON" "Financeiro" "Exploração de Imóveis"
create_asset "CYRE3" "Cyrela ON" "Consumo Cíclico" "Incorporação Imobiliária"
create_asset "DIRR3" "Direcional ON" "Consumo Cíclico" "Incorporação Imobiliária"
create_asset "MRVE3" "MRV ON" "Consumo Cíclico" "Incorporação Imobiliária"
create_asset "EZTC3" "EZTEC ON" "Consumo Cíclico" "Incorporação Imobiliária"
create_asset "MGLU3" "Magazine Luiza ON" "Consumo Cíclico" "Comércio Varejista"
create_asset "COGN3" "Cogna ON" "Consumo Cíclico" "Serviços Educacionais"
create_asset "FLRY3" "Fleury ON" "Saúde" "Medicina Diagnóstica"
create_asset "SLCE3" "SLC Agrícola ON" "Consumo não Cíclico" "Agricultura"
create_asset "SMFT3" "Smart Fit ON" "Consumo Cíclico" "Academias"
create_asset "RAIZ4" "Raízen PN" "Petróleo, Gás e Biocombustíveis" "Açúcar, Etanol e Distribuição"
create_asset "GOAU4" "Metalúrgica Gerdau PN" "Materiais Básicos" "Siderurgia e Metalurgia"
create_asset "ISAE4" "ISA Energia Brasil PN" "Utilidade Pública" "Transmissão de Energia"
create_asset "CSMG3" "Copasa ON" "Utilidade Pública" "Água e Saneamento"
create_asset "NTCO3" "Natura ON" "Consumo não Cíclico" "Produtos de Uso Pessoal"
create_asset "ALOS3" "Allos ON" "Financeiro" "Exploração de Imóveis"

# Empresas adicionais selecionadas pelo usuário
create_asset "JHSF3" "JHSF Participações ON" "Consumo Cíclico" "Incorporação Imobiliária e Shopping Centers"
create_asset "VULC3" "Vulcabras ON" "Consumo Cíclico" "Calçados"
create_asset "AZZA3" "Azzas 2154 ON" "Consumo Cíclico" "Vestuário e Calçados"
create_asset "PLPL3" "Plano & Plano ON" "Consumo Cíclico" "Incorporação Imobiliária"
create_asset "POMO3" "Marcopolo ON" "Bens Industriais" "Material Rodoviário"
create_asset "GMAT3" "Grupo Mateus ON" "Consumo não Cíclico" "Atacarejo e Varejo Alimentar"
create_asset "MELK3" "Melnick ON" "Consumo Cíclico" "Incorporação Imobiliária"
create_asset "EUCA4" "Eucatex PN" "Materiais Básicos" "Madeira e Materiais de Construção"
create_asset "TRIS3" "Trisul ON" "Consumo Cíclico" "Incorporação Imobiliária"
create_asset "MTRE3" "Mitre Realty ON" "Consumo Cíclico" "Incorporação Imobiliária"
create_asset "MDNE3" "Moura Dubeux ON" "Consumo Cíclico" "Incorporação Imobiliária"
create_asset "CEAB3" "C&A Modas ON" "Consumo Cíclico" "Varejo de Vestuário"
create_asset "GRND3" "Grendene ON" "Consumo Cíclico" "Calçados"
create_asset "CSED3" "Cruzeiro do Sul Educacional ON" "Consumo Cíclico" "Serviços Educacionais"
create_asset "POMO4" "Marcopolo PN" "Bens Industriais" "Material Rodoviário"
create_asset "RECV3" "PetroReconcavo ON" "Petróleo, Gás e Biocombustíveis" "Exploração e Produção"
create_asset "VLID3" "Valid ON" "Bens Industriais" "Serviços de Identificação e Tecnologia"
create_asset "SEER3" "Ser Educacional ON" "Consumo Cíclico" "Serviços Educacionais"
create_asset "SMTO3" "São Martinho ON" "Consumo não Cíclico" "Açúcar e Etanol"
create_asset "SBFG3" "Grupo SBF ON" "Consumo Cíclico" "Varejo de Artigos Esportivos"
create_asset "INTB3" "Intelbras ON" "Tecnologia da Informação" "Equipamentos de Comunicação e Segurança"
create_asset "KEPL3" "Kepler Weber ON" "Bens Industriais" "Máquinas e Equipamentos"
create_asset "PFRM3" "Profarma ON" "Consumo não Cíclico" "Distribuição de Medicamentos"
create_asset "MILS3" "Mills ON" "Bens Industriais" "Locação de Máquinas e Equipamentos"
create_asset "PGMN3" "Pague Menos ON" "Consumo não Cíclico" "Comércio de Medicamentos"
create_asset "TGMA3" "Tegma ON" "Bens Industriais" "Logística e Transporte Rodoviário"
create_asset "MDIA3" "M. Dias Branco ON" "Consumo não Cíclico" "Alimentos Processados"
create_asset "HYPE3" "Hypera Pharma ON" "Saúde" "Medicamentos"
create_asset "SAPR4" "Sanepar PN" "Utilidade Pública" "Água e Saneamento"
create_asset "RANI3" "Irani ON" "Materiais Básicos" "Papel e Embalagens"
create_asset "SAPR11" "Sanepar Units" "Utilidade Pública" "Água e Saneamento"
create_asset "BLAU3" "Blau Farmacêutica ON" "Saúde" "Medicamentos"
create_asset "SYNE3" "SYN Prop & Tech ON" "Financeiro" "Exploração de Imóveis"
create_asset "ALUP11" "Alupar Units" "Utilidade Pública" "Transmissão de Energia"
create_asset "FIQE3" "Unifique ON" "Comunicações" "Telecomunicações"
create_asset "BMOB3" "Bemobi ON" "Tecnologia da Informação" "Software e Serviços"
create_asset "PNVL3" "Panvel ON" "Consumo não Cíclico" "Comércio de Medicamentos"
create_asset "FESA4" "Ferbasa PN" "Materiais Básicos" "Siderurgia e Metalurgia"
create_asset "MATD3" "Mater Dei ON" "Saúde" "Serviços Médico-Hospitalares"
create_asset "ALPA4" "Alpargatas PN" "Consumo Cíclico" "Calçados"
create_asset "EGIE3" "Engie Brasil Energia ON" "Utilidade Pública" "Energia Elétrica"
create_asset "GGPS3" "GPS ON" "Bens Industriais" "Serviços Terceirizados"

# Empresas adicionais para estudo de position trade
# Financeiro e seguros
create_asset "CXSE3" "Caixa Seguridade ON" "Financeiro" "Seguros e Previdência"
create_asset "IGTI11" "Iguatemi Units" "Financeiro" "Exploração de Imóveis"

# Construção e propriedades
create_asset "CURY3" "Cury ON" "Consumo Cíclico" "Incorporação Imobiliária"
create_asset "LAVV3" "Lavvi ON" "Consumo Cíclico" "Incorporação Imobiliária"
create_asset "LOGG3" "LOG Commercial Properties ON" "Financeiro" "Exploração de Imóveis Logísticos"

# Indústria, autopeças e bens de capital
create_asset "LEVE3" "MAHLE Metal Leve ON" "Bens Industriais" "Autopeças"
create_asset "FRAS3" "Fras-le Mobility ON" "Bens Industriais" "Autopeças"
create_asset "RAPT4" "Randoncorp PN" "Bens Industriais" "Material Rodoviário e Autopeças"
create_asset "TUPY3" "Tupy ON" "Bens Industriais" "Motores, Componentes e Fundição"
create_asset "MYPK3" "Iochpe-Maxion ON" "Bens Industriais" "Autopeças"
create_asset "ROMI3" "Indústrias Romi ON" "Bens Industriais" "Máquinas e Equipamentos"

# Logística e infraestrutura
create_asset "PORT3" "Wilson Sons ON" "Bens Industriais" "Serviços Portuários e Marítimos"
create_asset "LOGN3" "Log-In Logística ON" "Bens Industriais" "Transporte Marítimo e Cabotagem"
create_asset "HBSA3" "Hidrovias do Brasil ON" "Bens Industriais" "Transporte Hidroviário"
create_asset "MOVI3" "Movida ON" "Consumo Cíclico" "Locação de Veículos"

# Agronegócio e alimentos
create_asset "TTEN3" "3tentos ON" "Consumo não Cíclico" "Insumos Agrícolas e Grãos"
create_asset "AGRO3" "BrasilAgro ON" "Consumo não Cíclico" "Agricultura e Terras Agrícolas"
create_asset "SOJA3" "Boa Safra Sementes ON" "Consumo não Cíclico" "Sementes e Insumos Agrícolas"

# Saúde e consumo
create_asset "ODPV3" "Odontoprev ON" "Saúde" "Planos Odontológicos"
create_asset "VIVA3" "Vivara ON" "Consumo Cíclico" "Joias e Acessórios"
create_asset "GUAR3" "Guararapes ON" "Consumo Cíclico" "Varejo de Vestuário"
create_asset "YDUQ3" "Yduqs ON" "Consumo Cíclico" "Serviços Educacionais"
create_asset "ANIM3" "Ânima Educação ON" "Consumo Cíclico" "Serviços Educacionais"
create_asset "CVCB3" "CVC Brasil ON" "Consumo Cíclico" "Viagens e Turismo"
create_asset "PCAR3" "Grupo Pão de Açúcar ON" "Consumo não Cíclico" "Varejo Alimentar"

# Tecnologia e serviços
create_asset "CSUD3" "CSU Digital ON" "Tecnologia da Informação" "Serviços de Tecnologia e Pagamentos"
create_asset "DESK3" "Desktop ON" "Comunicações" "Telecomunicações"

# Energia, saneamento e materiais básicos
create_asset "NEOE3" "Neoenergia ON" "Utilidade Pública" "Energia Elétrica"
create_asset "ORVR3" "Orizon ON" "Utilidade Pública" "Gestão de Resíduos e Biogás"
create_asset "CSNA3" "CSN ON" "Materiais Básicos" "Siderurgia"
create_asset "CMIN3" "CSN Mineração ON" "Materiais Básicos" "Mineração"
create_asset "USIM5" "Usiminas PNA" "Materiais Básicos" "Siderurgia"
create_asset "BRKM5" "Braskem PNA" "Materiais Básicos" "Petroquímicos"
create_asset "DXCO3" "Dexco ON" "Materiais Básicos" "Madeira e Materiais de Construção"

echo
echo "Processamento concluído."
echo "Cadastrados: ${SUCCESS_COUNT}"
echo "Já existentes: ${SKIPPED_COUNT}"
echo "Erros: ${ERROR_COUNT}"

if (( ERROR_COUNT > 0 )); then
  exit 1
fi
