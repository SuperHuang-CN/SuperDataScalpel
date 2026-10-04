import type { DataSourcePurpose } from '../model/dataSource';
import database from '../assets/database.svg';
import plug from '../assets/plug.svg';
import distribution from '../assets/hierarchy-2.svg';

const icons: Record<DataSourcePurpose, string> = {
  SOURCE: plug,
  STORAGE: database,
  DISTRIBUTION: distribution,
};

/** Local Tabler vectors share a stroke weight and inherit their semantic text color. */
export const DataSourcePurposeIcon = ({ purpose, size = 18 }: { purpose: DataSourcePurpose; size?: number }) => (
  <span
    aria-hidden="true"
    className="data-source-semantic-icon"
    style={{
      display: 'inline-block',
      flex: 'none',
      width: size,
      height: size,
      verticalAlign: 'middle',
      backgroundColor: 'currentColor',
      maskImage: `url("${icons[purpose]}")`,
      maskSize: 'contain',
      maskPosition: 'center',
      maskRepeat: 'no-repeat',
    }}
  />
);
