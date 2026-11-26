"""Gate shared-core line coverage from unit + API integration tests; no exclusions."""
from pathlib import Path
import xml.etree.ElementTree as ET
report = Path(__file__).resolve().parents[1] / 'backend/coverage/target/site/jacoco-aggregate/jacoco.xml'
root = ET.parse(report).getroot()
group = next(g for g in root.findall('group') if g.attrib['name'] == 'answerbase-core')
line = next(c for c in group.findall('counter') if c.attrib['type'] == 'LINE')
covered, missed = int(line.attrib['covered']), int(line.attrib['missed'])
ratio = covered / (covered + missed) if covered + missed else 0
print(f'Shared core line coverage: {ratio:.2%} ({covered}/{covered+missed}); required >=70%')
if ratio < .70:
    raise SystemExit('Coverage gate failed; add meaningful tests for uncovered behavior.')
