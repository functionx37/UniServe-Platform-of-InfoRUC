import React, { useRef, useState } from 'react';
import { adminApi } from './api/adminApi';
import * as XLSX from 'xlsx';

export const PartyProgressImport: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<any>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setLoading(true);
    try {
      const data = await file.arrayBuffer();
      const workbook = XLSX.read(data, { type: 'array' });
      const sheetName = workbook.SheetNames[0];
      const worksheet = workbook.Sheets[sheetName];
      
      const rawJson = XLSX.utils.sheet_to_json<any>(worksheet, { header: 1 });
      if (rawJson.length < 2) {
        throw new Error('表格数据为空或无数据行');
      }

      const headers = rawJson[0] as string[];
      const rows = rawJson.slice(1).map(row => {
        const obj: any = {};
        headers.forEach((h, i) => {
          let key = '';
          if (h.includes('学号')) key = 'studentNo';
          else if (h.includes('阶段') || h.includes('名称')) key = 'stageTitle';
          else if (h.includes('是否完成')) key = 'completed';
          else if (h.includes('备注')) key = 'notes';
          
          if (key) {
             obj[key] = row[i];
             // 尝试转换 boolean
             if (key === 'completed' && typeof row[i] === 'string') {
               obj[key] = row[i] === '是' || row[i] === 'true' || row[i] === '1';
             }
          }
        });
        return obj;
      }).filter(obj => obj.studentNo && obj.stageTitle);

      const res = await adminApi.importPartyProgress(file.name, rows);
      setResult(res.data || res);
    } catch (err: any) {
      alert('解析或上传出现错误: ' + err.message);
    } finally {
      setLoading(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  return (
    <div className="p-4 border rounded shadow-sm bg-white mt-4">
      <h2 className="text-lg font-bold mb-2">导入党团进度</h2>
      <p className="text-sm text-gray-500 mb-4">
        支持 Excel 文件 (.xlsx, .xls)。要求包含表头：学号、阶段名称（或节点名称）、是否完成（可选：是/否）。
      </p>
      <div className="flex items-center space-x-2">
         <input 
           type="file"
           ref={fileInputRef}
           accept=".xlsx, .xls, .csv"
           onChange={handleFileChange}
           style={{ display: 'none' }}
         />
         <button 
           disabled={loading}
           className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
           onClick={() => fileInputRef.current?.click()}
         >
           {loading ? '处理中...' : '选择文件并导入'}
         </button>
      </div>
      {result && (
        <div className="mt-4 p-3 bg-gray-50 rounded border text-sm">
           <div className="font-semibold mb-1">导入结果：{result.message}</div>
           {result.errors && result.errors.length > 0 && (
             <ul className="text-red-500 list-disc list-inside mt-2">
                {result.errors.map((e: string, i: number) => (
                  <li key={i}>{e}</li>
                ))}
             </ul>
           )}
        </div>
      )}
    </div>
  );
}
