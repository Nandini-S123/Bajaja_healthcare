SELECT department_id, COUNT(*) AS total_employees
FROM employees
GROUP BY department_id
ORDER BY total_employees DESC;
